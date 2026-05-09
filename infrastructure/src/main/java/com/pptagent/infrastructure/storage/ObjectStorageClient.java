package com.pptagent.infrastructure.storage;

import java.time.Duration;

public interface ObjectStorageClient {

    PresignedUpload createPresignedUpload(String objectKey, String contentType, Duration ttl);

    StoredObject putObject(String objectKey, String contentType, byte[] content);

    byte[] getObjectBytes(String objectKey);

    String publicUrl(String objectKey);
}
