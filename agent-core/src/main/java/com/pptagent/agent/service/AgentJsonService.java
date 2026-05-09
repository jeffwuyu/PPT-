package com.pptagent.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import org.springframework.stereotype.Service;

@Service
public class AgentJsonService {

    private final ObjectMapper objectMapper;

    public AgentJsonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String emptyObject() {
        return "{}";
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? objectMapper.createObjectNode() : value);
        } catch (JsonProcessingException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Failed to serialize agent JSON");
        }
    }

    public JsonNode fromJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (JsonProcessingException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Stored agent JSON is invalid");
        }
    }

    public ObjectNode objectNode() {
        return objectMapper.createObjectNode();
    }
}
