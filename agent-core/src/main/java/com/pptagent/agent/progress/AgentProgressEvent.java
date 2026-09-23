package com.pptagent.agent.progress;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AgentProgressEvent(
        String eventId,
        long sequence,
        String type,
        Long jobId,
        Long presentationId,
        Long checkpointId,
        Map<String, Object> data,
        Instant timestamp
) {

    public static AgentProgressEvent of(
            String type,
            Long jobId,
            Long presentationId,
            Long checkpointId,
            Map<String, Object> data
    ) {
        return new AgentProgressEvent(UUID.randomUUID().toString(), 0L, type, jobId, presentationId, checkpointId, data, Instant.now());
    }
}
