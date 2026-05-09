package com.pptagent.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pptagent.agent.dto.AgentResponse;
import com.pptagent.domain.agent.AgentCheckpointEntity;
import com.pptagent.domain.agent.AgentJobEntity;
import com.pptagent.domain.agent.AgentMemoryEntity;
import com.pptagent.domain.agent.AgentPlanEntity;
import com.pptagent.domain.agent.AgentToolCallEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentResponseMapper {

    private final AgentJsonService jsonService;

    public AgentResponseMapper(AgentJsonService jsonService) {
        this.jsonService = jsonService;
    }

    public AgentResponse toJobResponse(AgentJobEntity job, AgentPlanEntity plan, List<AgentCheckpointEntity> checkpoints) {
        return new AgentResponse(
                job.getId(),
                job.getPresentationId(),
                job.getStatus(),
                job.getPromptText(),
                job.getFailureReason(),
                plan == null ? null : plan.getPlanMarkdown(),
                checkpoints.stream().map(this::toCheckpointResponse).toList()
        );
    }

    public AgentResponse.CheckpointResponse toCheckpointResponse(AgentCheckpointEntity checkpoint) {
        return new AgentResponse.CheckpointResponse(
                checkpoint.getId(),
                checkpoint.getCheckpointOrder(),
                checkpoint.getTitle(),
                checkpoint.getObjective(),
                checkpoint.getStatus(),
                checkpoint.getMaxIterations(),
                checkpoint.getCurrentIterations(),
                checkpoint.getFailureReason()
        );
    }

    public AgentResponse.MemoryResponse toMemoryResponse(AgentMemoryEntity memory) {
        return new AgentResponse.MemoryResponse(
                memory.getMemoryScope(),
                memory.getMemoryKind(),
                memory.getContentText(),
                memory.isCompressed(),
                memory.isArchived()
        );
    }

    public AgentResponse.ToolCallResponse toToolCallResponse(AgentToolCallEntity toolCall) {
        JsonNode response = toolCall.getResponseJson() == null ? null : jsonService.fromJson(toolCall.getResponseJson());
        return new AgentResponse.ToolCallResponse(
                toolCall.getId(),
                toolCall.getToolName(),
                toolCall.getStatus(),
                response,
                toolCall.getErrorMessage()
        );
    }
}
