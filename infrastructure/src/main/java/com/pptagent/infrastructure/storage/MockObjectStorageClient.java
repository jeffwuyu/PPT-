package com.pptagent.infrastructure.storage;

import com.pptagent.infrastructure.config.CosStorageProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockObjectStorageClient implements ObjectStorageClient {

    private final CosStorageProperties properties;
    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private final Map<String, byte[]> objectBytes = new ConcurrentHashMap<>();

    public MockObjectStorageClient(CosStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public PresignedUpload createPresignedUpload(String objectKey, String contentType, Duration ttl) {
        return new PresignedUpload(objectKey, publicUrl(objectKey) + "?mockUploadToken=true", Instant.now().plus(ttl));
    }

    @Override
    public StoredObject putObject(String objectKey, String contentType, byte[] content) {
        if (properties.mockFailUpload()) {
            throw new ObjectStorageException("Mock COS upload failed");
        }
        StoredObject storedObject = new StoredObject(objectKey, publicUrl(objectKey), sha256(content));
        objects.put(objectKey, storedObject);
        objectBytes.put(objectKey, content.clone());
        return storedObject;
    }

    @Override
    public byte[] getObjectBytes(String objectKey) {
        byte[] content = objectBytes.get(objectKey);
        if (content == null) {
            throw new ObjectStorageException("Mock COS object not found");
        }
        return content.clone();
    }

    @Override
    public String publicUrl(String objectKey) {
        String baseUrl = properties.publicBaseUrl() == null || properties.publicBaseUrl().isBlank()
                ? "https://mock-cos.local"
                : properties.publicBaseUrl();
        return baseUrl.replaceAll("/$", "") + "/" + objectKey;
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(new String(content, StandardCharsets.ISO_8859_1).hashCode());
        }
    }
}
