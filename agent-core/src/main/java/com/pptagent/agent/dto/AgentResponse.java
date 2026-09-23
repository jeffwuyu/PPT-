package com.pptagent.agent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pptagent.domain.agent.AgentCheckpointStatus;
import com.pptagent.domain.agent.AgentJobStatus;
import com.pptagent.domain.agent.AgentMemoryScope;
import com.pptagent.domain.agent.AgentToolCallStatus;

import java.util.List;

public record AgentResponse(
        Long id,
        Long presentationId,
        AgentJobStatus status,
        String prompt,
        String failureReason,
        String planMarkdown,
        String translatedPlanMarkdown,
        List<CheckpointResponse> checkpoints
) {

    public record OutlineResponse(
            Long jobId,
            Long planId,
            int revision,
            boolean approved,
            Integer approvedRevision,
            JsonNode outline
    ) {
    }
    public record CheckpointResponse(
            Long id,
            int order,
            String title,
            String objective,
            AgentCheckpointStatus status,
            int maxIterations,
            int currentIterations,
            String failureReason
    ) {
    }

    public record MemoryResponse(
            AgentMemoryScope scope,
            String kind,
            String content,
            boolean compressed,
            boolean archived
    ) {
    }

    public record ToolCallResponse(
            Long id,
            String toolName,
            AgentToolCallStatus status,
            JsonNode response,
            String errorMessage
    ) {
    }
}
