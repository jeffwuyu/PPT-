package com.pptagent.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ppt.agent")
public record AgentProperties(
        String defaultModel,
        int maxPlanSlides,
        int defaultCheckpointMaxIterations,
        int memoryCompressionThresholdChars,
        int recentMessageLimit,
        boolean mockLlmEnabled,
        boolean mcpToolsEnabled,
        boolean mockBailianEnabled,
        String bailianApiKey,
        String qwenBaseUrl,
        String imageGenerationUrl,
        String imageModel,
        String imageSize,
        int requestTimeoutSeconds
) {
    public String effectiveDefaultModel() {
        return hasText(defaultModel) ? defaultModel : "qwen-plus";
    }

    public String effectiveQwenBaseUrl() {
        return hasText(qwenBaseUrl) ? qwenBaseUrl : "https://dashscope.aliyuncs.com/compatible-mode/v1";
    }

    public String effectiveImageGenerationUrl() {
        return hasText(imageGenerationUrl)
                ? imageGenerationUrl
                : "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    }

    public String effectiveImageModel() {
        return hasText(imageModel) ? imageModel : "qwen-image-2.0-pro";
    }

    public String effectiveImageSize() {
        return hasText(imageSize) ? imageSize : "2688*1536";
    }

    public int effectiveRequestTimeoutSeconds() {
        return requestTimeoutSeconds > 0 ? requestTimeoutSeconds : 60;
    }

    public boolean hasBailianApiKey() {
        return hasText(bailianApiKey);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
