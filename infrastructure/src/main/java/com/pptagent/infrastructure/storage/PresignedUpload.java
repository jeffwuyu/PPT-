package com.pptagent.infrastructure.storage;

import java.time.Instant;

public record PresignedUpload(
        String objectKey,
        String uploadUrl,
        Instant expiresAt
) {
}
