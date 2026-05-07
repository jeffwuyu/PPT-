package com.pptagent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ppt.redis")
public record RedisPlatformProperties(
        String namespace,
        String collaborationTopic,
        String ragIndexName
) {
}
