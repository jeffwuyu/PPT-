package com.pptagent.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class AgentSpringAiTools {

    @Tool(name = "jsoup_web_fetch", description = "Fetch webpage text and image candidates for PPT planning through the jsoup tool facade.")
    public String jsoupWebFetch(
            @ToolParam(description = "HTTP or HTTPS URL to fetch") String url
    ) {
        return "{\"tool\":\"jsoup_web_fetch\",\"url\":\"%s\",\"note\":\"Use AgentToolService for validated, persisted execution.\"}"
                .formatted(escape(url));
    }

    @Tool(name = "bailian_image_generate", description = "Generate a PPT image through the Bailian/DashScope image tool facade.")
    public String bailianImageGenerate(
            @ToolParam(description = "Image generation prompt") String prompt
    ) {
        return "{\"tool\":\"bailian_image_generate\",\"prompt\":\"%s\",\"note\":\"Use AgentToolService for validated, persisted execution.\"}"
                .formatted(escape(prompt));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
