package com.pptagent.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pptagent.agent.config.AgentProperties;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class BailianImageClient {

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public BailianImageClient(AgentProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newHttpClient());
    }

    BailianImageClient(AgentProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public JsonNode generate(String prompt, String negativePrompt) {
        requireApiKey();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.effectiveImageGenerationUrl()))
                    .timeout(Duration.ofSeconds(properties.effectiveRequestTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.bailianApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(prompt, negativePrompt)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Bailian image request failed: HTTP " + response.statusCode());
            }
            String imageUrl = extractImageUrl(body);
            ObjectNode result = objectMapper.createObjectNode();
            result.put("provider", "bailian-dashscope");
            result.put("mock", false);
            result.put("model", properties.effectiveImageModel());
            result.put("prompt", prompt);
            result.put("imageUrl", imageUrl);
            copyText(body, result, "request_id", "requestId");
            JsonNode usage = body.get("usage");
            if (usage != null && usage.isObject()) {
                result.set("usage", usage);
            }
            return result;
        } catch (PlatformException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Bailian image request was interrupted");
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Bailian image request failed");
        }
    }

    private String requestBody(String prompt, String negativePrompt) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.effectiveImageModel());
        ObjectNode message = body.putObject("input").putArray("messages").addObject();
        message.put("role", "user");
        message.putArray("content").addObject().put("text", prompt);

        ObjectNode parameters = body.putObject("parameters");
        if (negativePrompt != null && !negativePrompt.isBlank()) {
            parameters.put("negative_prompt", negativePrompt);
        }
        parameters.put("prompt_extend", true);
        parameters.put("watermark", false);
        parameters.put("n", 1);
        parameters.put("size", properties.effectiveImageSize());
        return objectMapper.writeValueAsString(body);
    }

    private String extractImageUrl(JsonNode body) {
        JsonNode content = body.at("/output/choices/0/message/content");
        if (content.isArray()) {
            for (JsonNode item : content) {
                JsonNode image = item.get("image");
                if (image != null && image.isTextual() && !image.asText().isBlank()) {
                    return image.asText();
                }
            }
        }
        JsonNode code = body.get("code");
        JsonNode message = body.get("message");
        if (code != null || message != null) {
            throw new PlatformException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Bailian image response failed: " + text(code) + " " + text(message)
            );
        }
        throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Bailian image response did not contain image URL");
    }

    private void copyText(JsonNode source, ObjectNode target, String sourceField, String targetField) {
        JsonNode value = source.get(sourceField);
        if (value != null && value.isTextual()) {
            target.put(targetField, value.asText());
        }
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText();
    }

    private void requireApiKey() {
        if (!properties.hasBailianApiKey()) {
            throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Bailian API key is required when mock Bailian is disabled");
        }
    }
}
