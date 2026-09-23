package com.pptagent.api.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerationSubmissionRequest(
        @NotBlank @Size(max = 128) String idempotencyKey
) { }
