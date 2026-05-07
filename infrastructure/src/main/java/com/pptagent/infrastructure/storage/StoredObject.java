package com.pptagent.infrastructure.storage;

public record StoredObject(
        String objectKey,
        String publicUrl,
        String etag
) {
}
