package com.pptagent.infrastructure.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
import com.pptagent.infrastructure.config.CosStorageProperties;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public class TencentCosObjectStorageClient implements ObjectStorageClient {

    private final CosStorageProperties properties;
    private final COSClient cosClient;

    public TencentCosObjectStorageClient(CosStorageProperties properties) {
        this.properties = properties;
        COSCredentials credentials = new BasicCOSCredentials(properties.secretId(), properties.secretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(properties.region()));
        this.cosClient = new COSClient(credentials, clientConfig);
    }

    @Override
    public PresignedUpload createPresignedUpload(String objectKey, String contentType, Duration ttl) {
        try {
            Date expiration = Date.from(Instant.now().plus(ttl));
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    properties.bucket(),
                    objectKey,
                    HttpMethodName.PUT
            );
            request.setExpiration(expiration);
            request.addRequestParameter("Content-Type", contentType);
            return new PresignedUpload(objectKey, cosClient.generatePresignedUrl(request).toString(), expiration.toInstant());
        } catch (RuntimeException exception) {
            throw new ObjectStorageException("Failed to create COS presigned upload", exception);
        }
    }

    @Override
    public StoredObject putObject(String objectKey, String contentType, byte[] content) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(content.length);
            PutObjectRequest request = new PutObjectRequest(
                    properties.bucket(),
                    objectKey,
                    new ByteArrayInputStream(content),
                    metadata
            );
            PutObjectResult result = cosClient.putObject(request);
            return new StoredObject(objectKey, publicUrl(objectKey), result.getETag());
        } catch (RuntimeException exception) {
            throw new ObjectStorageException("Failed to upload object to COS", exception);
        }
    }

    @Override
    public byte[] getObjectBytes(String objectKey) {
        try (COSObject object = cosClient.getObject(properties.bucket(), objectKey)) {
            return object.getObjectContent().readAllBytes();
        } catch (Exception exception) {
            throw new ObjectStorageException("Failed to read object from COS", exception);
        }
    }

    @Override
    public String publicUrl(String objectKey) {
        if (properties.publicBaseUrl() != null && !properties.publicBaseUrl().isBlank()) {
            return properties.publicBaseUrl().replaceAll("/$", "") + "/" + objectKey;
        }
        return "https://" + properties.bucket() + ".cos." + properties.region() + ".myqcloud.com/" + objectKey;
    }
}
