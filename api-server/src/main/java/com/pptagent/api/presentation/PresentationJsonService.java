package com.pptagent.api.presentation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import org.springframework.stereotype.Service;

@Service
public class PresentationJsonService {

    private static final String EMPTY_JSON = "{}";

    private final ObjectMapper objectMapper;

    public PresentationJsonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode defaultObject(JsonNode node) {
        return node == null || node.isNull() ? objectMapper.createObjectNode() : node;
    }

    public String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(defaultObject(node));
        } catch (JsonProcessingException exception) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Invalid JSON content");
        }
    }

    public JsonNode fromJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? EMPTY_JSON : json);
        } catch (JsonProcessingException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Stored JSON content is invalid");
        }
    }

    public int positive(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, fieldName + " must be positive");
        }
        return value;
    }

    public int positiveOrDefault(Integer value, int defaultValue, String fieldName) {
        return value == null ? defaultValue : positive(value, fieldName);
    }

    public void positiveDouble(Double value, String fieldName) {
        if (value == null || value <= 0) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, fieldName + " must be positive");
        }
    }

    public double requiredDouble(Double value, String fieldName) {
        if (value == null) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }

    public double defaultDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    public int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
