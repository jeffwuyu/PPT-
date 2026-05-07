package com.pptagent.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ppt.agent")
public record AgentProperties(
        String defaultModel,
        int maxPlanSlides,
        boolean mockLlmEnabled,
        boolean mcpToolsEnabled
) {
}
