package com.pptagent.agent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class AgentRequests {

    private AgentRequests() {
    }

    public record CreateJobRequest(
            @NotNull Long presentationId,
            String prompt,
            List<Long> fileAssetIds,
            List<Long> imageAssetIds
    ) {
    }

    public record ToolCallRequest(
            @NotNull String toolName,
            @NotNull JsonNode arguments
    ) {
    }

    public record UpdateOutlineRequest(
            @NotNull Integer expectedRevision,
            @NotNull JsonNode outline,
            Boolean approve
    ) {
    }
}
