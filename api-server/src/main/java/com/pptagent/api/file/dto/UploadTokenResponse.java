package com.pptagent.api.file.dto;

import java.time.Instant;

public record UploadTokenResponse(
        Long fileId,
        String objectKey,
        String uploadUrl,
        Instant expiresAt
) {
}
