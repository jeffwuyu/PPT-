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

    @Tool(name = "image_candidate_search", description = "Find PPT image candidates by topic or source page without vector search.")
    public String imageCandidateSearch(
            @ToolParam(description = "PPT topic or slide visual need") String topic
    ) {
        return "{\"tool\":\"image_candidate_search\",\"topic\":\"%s\",\"note\":\"Use AgentToolService for validated, persisted execution.\"}"
                .formatted(escape(topic));
    }

    @Tool(name = "image_vlm_evaluate", description = "Evaluate whether an image candidate is suitable for PPT use.")
    public String imageVlmEvaluate(
            @ToolParam(description = "Image candidate JSON") String candidateJson
    ) {
        return "{\"tool\":\"image_vlm_evaluate\",\"candidateJson\":\"%s\",\"note\":\"Use AgentToolService for validated, persisted execution.\"}"
                .formatted(escape(candidateJson));
    }

    @Tool(name = "image_asset_save", description = "Persist evaluated PPT image metadata and tags.")
    public String imageAssetSave(
            @ToolParam(description = "Evaluated image JSON") String imageJson
    ) {
        return "{\"tool\":\"image_asset_save\",\"imageJson\":\"%s\",\"note\":\"Use AgentToolService for validated, persisted execution.\"}"
                .formatted(escape(imageJson));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
