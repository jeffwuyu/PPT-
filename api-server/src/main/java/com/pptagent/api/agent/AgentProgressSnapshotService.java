package com.pptagent.api.agent;

import com.pptagent.agent.dto.AgentResponse;
import com.pptagent.agent.service.AgentJobService;
import com.pptagent.domain.agent.AgentArtifactRefEntity;
import com.pptagent.domain.agent.AgentMessageEntity;
import com.pptagent.domain.agent.AgentToolCallEntity;
import com.pptagent.domain.agent.repository.AgentArtifactRefRepository;
import com.pptagent.domain.agent.repository.AgentMessageRepository;
import com.pptagent.domain.agent.repository.AgentToolCallRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentProgressSnapshotService {

    private final AgentJobService agentJobService;
    private final AgentMessageRepository messageRepository;
    private final AgentToolCallRepository toolCallRepository;
    private final AgentArtifactRefRepository artifactRefRepository;

    public AgentProgressSnapshotService(
            AgentJobService agentJobService,
            AgentMessageRepository messageRepository,
            AgentToolCallRepository toolCallRepository,
            AgentArtifactRefRepository artifactRefRepository
    ) {
        this.agentJobService = agentJobService;
        this.messageRepository = messageRepository;
        this.toolCallRepository = toolCallRepository;
        this.artifactRefRepository = artifactRefRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(Long userId, Long jobId) {
        AgentResponse job = agentJobService.getJob(userId, jobId);
        Map<Long, Map<String, Object>> checkpointById = new LinkedHashMap<>();
        for (AgentResponse.CheckpointResponse checkpoint : job.checkpoints()) {
            Map<String, Object> checkpointData = new LinkedHashMap<>();
            checkpointData.put("id", checkpoint.id());
            checkpointData.put("order", checkpoint.order());
            checkpointData.put("title", checkpoint.title());
            checkpointData.put("objective", checkpoint.objective());
            checkpointData.put("status", checkpoint.status());
            checkpointData.put("currentIterations", checkpoint.currentIterations());
            checkpointData.put("maxIterations", checkpoint.maxIterations());
            checkpointData.put("failureReason", checkpoint.failureReason());
            checkpointData.put("messages", new ArrayList<>());
            checkpointData.put("toolCalls", new ArrayList<>());
            checkpointData.put("artifacts", new ArrayList<>());
            checkpointById.put(checkpoint.id(), checkpointData);
        }

        List<Map<String, Object>> jobMessages = new ArrayList<>();
        for (AgentMessageEntity message : messageRepository.findByJobIdOrderByIdAsc(job.id())) {
            Map<String, Object> data = messagePayload(message);
            if (message.getCheckpointId() == null) {
                jobMessages.add(data);
            } else {
                addToCheckpoint(checkpointById, message.getCheckpointId(), "messages", data);
            }
        }
        for (AgentToolCallEntity toolCall : toolCallRepository.findByJobId(job.id())) {
            addToCheckpoint(checkpointById, toolCall.getCheckpointId(), "toolCalls", toolCallPayload(toolCall));
        }
        for (AgentArtifactRefEntity artifact : artifactRefRepository.findByJobIdOrderByIdAsc(job.id())) {
            addToCheckpoint(checkpointById, artifact.getCheckpointId(), "artifacts", artifactPayload(artifact));
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", "snapshot");
        snapshot.put("timestamp", Instant.now());
        Map<String, Object> jobData = new LinkedHashMap<>();
        jobData.put("id", job.id());
        jobData.put("presentationId", job.presentationId());
        jobData.put("status", job.status());
        jobData.put("prompt", job.prompt());
        jobData.put("failureReason", job.failureReason());
        jobData.put("planMarkdown", job.planMarkdown());
        jobData.put("translatedPlanMarkdown", job.translatedPlanMarkdown());
        snapshot.put("job", jobData);
        snapshot.put("messages", jobMessages);
        snapshot.put("checkpoints", List.copyOf(checkpointById.values()));
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private void addToCheckpoint(Map<Long, Map<String, Object>> checkpointById, Long checkpointId, String key, Map<String, Object> value) {
        if (checkpointId == null) {
            return;
        }
        Map<String, Object> checkpoint = checkpointById.get(checkpointId);
        if (checkpoint == null) {
            return;
        }
        ((List<Map<String, Object>>) checkpoint.get(key)).add(value);
    }

    private Map<String, Object> messagePayload(AgentMessageEntity message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", message.getId());
        data.put("checkpointId", message.getCheckpointId());
        data.put("roleName", message.getRoleName());
        data.put("contentText", message.getContentText());
        data.put("metadataJson", message.getMetadataJson());
        data.put("createdAt", message.getCreatedAt());
        return data;
    }

    private Map<String, Object> toolCallPayload(AgentToolCallEntity toolCall) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolCallId", toolCall.getId());
        data.put("checkpointId", toolCall.getCheckpointId());
        data.put("toolName", toolCall.getToolName());
        data.put("requestJson", toolCall.getRequestJson());
        data.put("responseJson", toolCall.getResponseJson());
        data.put("status", toolCall.getStatus());
        data.put("durationMs", toolCall.getDurationMs());
        data.put("errorMessage", toolCall.getErrorMessage());
        data.put("createdAt", toolCall.getCreatedAt());
        return data;
    }

    private Map<String, Object> artifactPayload(AgentArtifactRefEntity artifact) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("artifactRefId", artifact.getId());
        data.put("checkpointId", artifact.getCheckpointId());
        data.put("artifactType", artifact.getArtifactType());
        data.put("artifactId", artifact.getArtifactId());
        data.put("actionName", artifact.getActionName());
        data.put("active", artifact.isActive());
        data.put("metadataJson", artifact.getMetadataJson());
        data.put("createdAt", artifact.getCreatedAt());
        return data;
    }
}
