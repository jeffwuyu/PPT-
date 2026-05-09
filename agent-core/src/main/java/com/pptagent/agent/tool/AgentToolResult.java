package com.pptagent.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentToolResult(Long toolCallId, String toolName, JsonNode response) {
}
