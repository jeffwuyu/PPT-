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
        Instant timestamp,
        String pageId,
        Long aggregateRevision
) {

    public AgentProgressEvent(String eventId, long sequence, String type, Long jobId,
                              Long presentationId, Long checkpointId, Map<String, Object> data,
                              Instant timestamp) {
        this(eventId, sequence, type, jobId, presentationId, checkpointId, data, timestamp,
                data == null || data.get("pageId") == null ? null : String.valueOf(data.get("pageId")),
                data == null || data.get("aggregateRevision") == null ? null
                        : ((Number) data.get("aggregateRevision")).longValue());
    }

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
