package com.pptagent.api.agent.dto;

import java.time.Instant;
import java.util.List;

public record GenerationJobResponse(
        Long id,
        Long agentJobId,
        String status,
        long revision,
        int approvedOutlineRevision,
        int totalPages,
        int completedPages,
        String failureReason,
        List<PageTaskResponse> pages,
        Instant createdAt,
        Instant updatedAt
) {
    public record PageTaskResponse(String pageId, int pageOrder, String status, int attempts, String failureReason) { }
}
