package com.pptagent.agent.progress;

import java.time.Instant;
import java.util.Map;

public record AgentProgressEvent(
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
        return new AgentProgressEvent(type, jobId, presentationId, checkpointId, data, Instant.now());
    }
}
