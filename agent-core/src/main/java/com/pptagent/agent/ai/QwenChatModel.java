package com.pptagent.agent.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pptagent.agent.config.AgentProperties;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "ppt.agent", name = "mock-llm-enabled", havingValue = "false")
public class QwenChatModel implements ChatModel {

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public QwenChatModel(AgentProperties properties) {
        this(properties, new ObjectMapper(), HttpClient.newHttpClient());
    }

    QwenChatModel(AgentProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        requireApiKey();
        try {
            HttpRequest request = HttpRequest.newBuilder(chatCompletionsUri())
                    .timeout(Duration.ofSeconds(properties.effectiveRequestTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.bailianApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(prompt)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Qwen chat request failed: HTTP " + response.statusCode());
            }
            String content = extractAssistantText(objectMapper.readTree(response.body()));
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        } catch (PlatformException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Qwen chat request was interrupted");
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Qwen chat request failed");
        }
    }

    private URI chatCompletionsUri() {
        return URI.create(trimTrailingSlash(properties.effectiveQwenBaseUrl()) + "/chat/completions");
    }

    private String requestBody(Prompt prompt) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.effectiveDefaultModel());
        ArrayNode messages = body.putArray("messages");
        for (Message instruction : prompt.getInstructions()) {
            ObjectNode message = messages.addObject();
            message.put("role", role(instruction.getMessageType()));
            message.put("content", instruction.getText());
        }
        return objectMapper.writeValueAsString(body);
    }

    private String role(MessageType type) {
        if (type == MessageType.SYSTEM) {
            return "system";
        }
        if (type == MessageType.ASSISTANT) {
            return "assistant";
        }
        return "user";
    }

    private String extractAssistantText(JsonNode response) {
        JsonNode content = response.at("/choices/0/message/content");
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : content) {
                JsonNode text = item.get("text");
                if (text != null && text.isTextual()) {
                    builder.append(text.asText());
                }
            }
            if (!builder.isEmpty()) {
                return builder.toString();
            }
        }
        throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Qwen chat response did not contain assistant content");
    }

    private void requireApiKey() {
        if (!properties.hasBailianApiKey()) {
            throw new PlatformException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Bailian API key is required when mock LLM is disabled");
        }
    }

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
