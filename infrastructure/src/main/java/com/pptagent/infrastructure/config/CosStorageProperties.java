package com.pptagent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ppt.cos")
public record CosStorageProperties(
        String region,
        String bucket,
        String secretId,
        String secretKey,
        String publicBaseUrl,
        boolean mockEnabled,
        boolean mockFailUpload
) {
}
