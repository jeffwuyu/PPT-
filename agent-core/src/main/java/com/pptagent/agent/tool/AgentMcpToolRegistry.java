package com.pptagent.agent.tool;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentMcpToolRegistry {

    public List<String> toolNames() {
        return List.of("jsoup_web_fetch", "bailian_image_generate");
    }
}
