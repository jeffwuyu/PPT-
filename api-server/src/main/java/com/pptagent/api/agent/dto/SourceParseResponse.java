package com.pptagent.api.agent.dto;

import java.time.Instant;
import java.util.List;

public record SourceParseResponse(
        Long id,
        Long fileAssetId,
        Long agentJobId,
        String status,
        long revision,
        String parserVersion,
        String sourceHash,
        int totalBlocks,
        String failureReason,
        List<BlockResponse> blocks,
        Instant createdAt,
        Instant updatedAt
) {
    public record BlockResponse(String blockId, String type, String text, String locatorJson) { }
}
