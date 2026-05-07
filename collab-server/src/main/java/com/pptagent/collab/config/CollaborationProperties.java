package com.pptagent.collab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ppt.collaboration")
public record CollaborationProperties(
        String websocketEndpoint,
        int snapshotIntervalSeconds,
        boolean yjsPersistenceEnabled
) {
}
