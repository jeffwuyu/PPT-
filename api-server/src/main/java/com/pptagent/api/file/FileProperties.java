package com.pptagent.api.file;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties(prefix = "ppt.files")
public record FileProperties(
        DataSize maxUploadSize,
        Duration presignedUploadTtl
) {
}
