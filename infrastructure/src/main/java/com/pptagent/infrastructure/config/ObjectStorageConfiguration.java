package com.pptagent.infrastructure.config;

import com.pptagent.infrastructure.storage.MockObjectStorageClient;
import com.pptagent.infrastructure.storage.ObjectStorageClient;
import com.pptagent.infrastructure.storage.TencentCosObjectStorageClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObjectStorageConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ppt.cos", name = "mock-enabled", havingValue = "true", matchIfMissing = true)
    ObjectStorageClient mockObjectStorageClient(CosStorageProperties properties) {
        return new MockObjectStorageClient(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ppt.cos", name = "mock-enabled", havingValue = "false")
    ObjectStorageClient tencentCosObjectStorageClient(CosStorageProperties properties) {
        return new TencentCosObjectStorageClient(properties);
    }
}
