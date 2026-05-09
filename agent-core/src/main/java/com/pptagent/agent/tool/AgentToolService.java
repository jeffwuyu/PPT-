package com.pptagent.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pptagent.agent.config.AgentProperties;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.agent.AgentToolCallEntity;
import com.pptagent.domain.agent.AgentToolCallStatus;
import com.pptagent.domain.agent.repository.AgentToolCallRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class AgentToolService {

    private final AgentToolCallRepository toolCallRepository;
    private final AgentToolSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    private final BailianImageClient bailianImageClient;

    public AgentToolService(
            AgentToolCallRepository toolCallRepository,
            AgentToolSchemaValidator schemaValidator,
            ObjectMapper objectMapper,
            AgentProperties agentProperties,
            BailianImageClient bailianImageClient
    ) {
        this.toolCallRepository = toolCallRepository;
        this.schemaValidator = schemaValidator;
        this.objectMapper = objectMapper;
        this.agentProperties = agentProperties;
        this.bailianImageClient = bailianImageClient;
    }

    @Transactional(noRollbackFor = PlatformException.class)
    public AgentToolResult callTool(Long jobId, Long checkpointId, String toolName, JsonNode arguments) {
        String requestJson = arguments == null ? "{}" : arguments.toString();
        AgentToolCallEntity call = new AgentToolCallEntity(jobId, checkpointId, toolName, requestJson);
        toolCallRepository.insert(call);
        Instant start = Instant.now();
        try {
            schemaValidator.validate(toolName, arguments);
            JsonNode response = switch (toolName) {
                case "jsoup_web_fetch" -> fetchWeb(arguments.get("url").asText());
                case "bailian_image_generate" -> generateImage(arguments);
                default -> throw new PlatformException(ErrorCode.BAD_REQUEST, "Unknown agent tool");
            };
            call.finish(AgentToolCallStatus.SUCCESS, response.toString(), elapsed(start), null);
            toolCallRepository.updateById(call);
            return new AgentToolResult(call.getId(), toolName, response);
        } catch (PlatformException exception) {
            AgentToolCallStatus status = exception.getErrorCode() == ErrorCode.BAD_REQUEST
                    ? AgentToolCallStatus.REJECTED
                    : AgentToolCallStatus.FAILED;
            call.finish(status, null, elapsed(start), exception.getMessage());
            toolCallRepository.updateById(call);
            throw exception;
        } catch (Exception exception) {
            call.finish(AgentToolCallStatus.FAILED, null, elapsed(start), exception.getMessage());
            toolCallRepository.updateById(call);
            throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Agent tool call failed");
        }
    }

    private JsonNode fetchWeb(String url) throws Exception {
        if (url.contains("example.test") || url.contains("mock.local")) {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("url", url);
            response.put("title", "Mock fetched page");
            response.put("text", "Mock Jsoup content for agent planning.");
            response.set("images", objectMapper.createArrayNode());
            return response;
        }
        Document document = Jsoup.connect(url).timeout(3000).get();
        ObjectNode response = objectMapper.createObjectNode();
        response.put("url", url);
        response.put("title", document.title());
        response.put("text", document.body() == null ? "" : abbreviate(document.body().text(), 1200));
        ArrayNode images = objectMapper.createArrayNode();
        for (Element image : document.select("img[src]")) {
            if (images.size() >= 8) {
                break;
            }
            images.add(image.absUrl("src"));
        }
        response.set("images", images);
        return response;
    }

    private JsonNode generateImage(JsonNode arguments) {
        String prompt = imagePrompt(arguments);
        if (!agentProperties.mockBailianEnabled()) {
            return bailianImageClient.generate(prompt, text(arguments, "negativePrompt"));
        }
        ObjectNode response = objectMapper.createObjectNode();
        response.put("provider", "bailian-dashscope");
        response.put("mock", true);
        response.put("model", agentProperties.effectiveImageModel());
        response.put("prompt", prompt);
        response.put("imageUrl", "https://mock-bailian.example.test/generated/%08x.png".formatted(prompt.hashCode()));
        response.put("licenseNote", "Mock image generation result for Phase 06 tests");
        return response;
    }

    private String imagePrompt(JsonNode arguments) {
        String prompt = arguments.get("prompt").asText();
        String style = text(arguments, "style");
        return style.isBlank() ? prompt : prompt + "\nStyle: " + style;
    }

    private String text(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        return value == null || !value.isTextual() ? "" : value.asText();
    }

    private long elapsed(Instant start) {
        return Math.max(0, Duration.between(start, Instant.now()).toMillis());
    }

    private String abbreviate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
