package com.pptagent.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import org.springframework.stereotype.Service;

@Service
public class AgentToolSchemaValidator {

    public void validate(String toolName, JsonNode arguments) {
        if (toolName == null || toolName.isBlank()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "toolName is required");
        }
        if (arguments == null || !arguments.isObject()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "tool arguments must be an object");
        }
        switch (toolName) {
            case "jsoup_web_fetch" -> validateJsoup(arguments);
            case "bailian_image_generate" -> validateBailian(arguments);
            default -> throw new PlatformException(ErrorCode.BAD_REQUEST, "Unknown agent tool");
        }
    }

    private void validateJsoup(JsonNode arguments) {
        String url = text(arguments, "url");
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "jsoup_web_fetch.url must be an http(s) URL");
        }
    }

    private void validateBailian(JsonNode arguments) {
        if (text(arguments, "prompt").isBlank()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "bailian_image_generate.prompt is required");
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, field + " must be a string");
        }
        return value.asText();
    }
}
