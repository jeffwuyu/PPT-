package com.pptagent.api.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SourceParseRequest(
        @NotNull Long fileAssetId,
        Long agentJobId,
        @NotBlank @Size(max = 128) String idempotencyKey
) { }
