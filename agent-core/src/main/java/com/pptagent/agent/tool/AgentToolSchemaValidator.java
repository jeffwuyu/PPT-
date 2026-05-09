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
            case "image_candidate_search" -> validateImageCandidateSearch(arguments);
            case "image_vlm_evaluate" -> validateImageVlmEvaluate(arguments);
            case "image_asset_save" -> validateImageAssetSave(arguments);
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

    private void validateImageCandidateSearch(JsonNode arguments) {
        if (text(arguments, "topic").isBlank()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "image_candidate_search.topic is required");
        }
        JsonNode sourceUrl = arguments.get("sourceUrl");
        if (sourceUrl != null && !sourceUrl.isNull()) {
            String url = optionalText(arguments, "sourceUrl");
            if (!url.isBlank() && !(url.startsWith("http://") || url.startsWith("https://"))) {
                throw new PlatformException(ErrorCode.BAD_REQUEST, "image_candidate_search.sourceUrl must be an http(s) URL");
            }
        }
        JsonNode limit = arguments.get("limit");
        if (limit != null && (!limit.canConvertToInt() || limit.asInt() <= 0)) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "image_candidate_search.limit must be a positive integer");
        }
    }

    private void validateImageVlmEvaluate(JsonNode arguments) {
        JsonNode candidate = arguments.get("candidate");
        if (candidate == null || !candidate.isObject()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "image_vlm_evaluate.candidate must be an object");
        }
    }

    private void validateImageAssetSave(JsonNode arguments) {
        JsonNode image = arguments.get("image");
        if (image == null || !image.isObject()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "image_asset_save.image must be an object");
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, field + " must be a string");
        }
        return value.asText();
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (!value.isTextual()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, field + " must be a string");
        }
        return value.asText();
    }
}
