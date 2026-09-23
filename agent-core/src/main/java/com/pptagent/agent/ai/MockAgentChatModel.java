package com.pptagent.agent.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "ppt.agent", name = "mock-llm-enabled", havingValue = "true", matchIfMissing = true)
public class MockAgentChatModel implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
        String input = prompt.getContents();
        String content;
        if (input.contains("COMPRESS_MEMORY")) {
            content = "Compressed memory: " + abbreviate(input.replace("COMPRESS_MEMORY", ""), 180);
        } else if (input.contains("PAGE_CONTENT_REQUEST")) {
            content = pageContent(input);
        } else if (input.contains("TRANSLATE_PLAN_TO_CHINESE")) {
            content = """
                    # PPT 制作计划

                    - [ ] 分析素材并明确演示文稿叙事。
                    - [ ] 创建包含标题、受众和目标的开场页。
                    - [ ] 制作核心内容页，使用简洁要点和视觉占位。
                    - [ ] 检查一致性并准备生成的幻灯片 JSON。
                    """;
        } else if (input.contains("REACT_CHECKPOINT")) {
            content = """
                    {"thought":"Checkpoint is clear and bounded.","action":"CREATE_SLIDE","needsReplan":false,"cancelCurrent":false}
                    """;
        } else if (input.contains("REPLAN_CHECKPOINT")) {
            content = """
                    {"thought":"The checkpoint no longer matches the plan.","action":"REPLAN","needsReplan":true,"cancelCurrent":true}
                    """;
        } else {
            content = """
                    # PPT Production Plan

                    - [ ] Analyze source material and define the deck narrative.
                    - [ ] Create the opening slide with title, audience, and goal.
                    - [ ] Build the core content slide with concise bullets and visual placeholder.
                    - [ ] Review consistency and prepare generated slide JSON for publishing.
                    """;
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private String pageContent(String input) {
        try {
            int markerEnd = input.indexOf("PAGE_CONTENT_REQUEST") + "PAGE_CONTENT_REQUEST".length();
            int payloadStart = input.indexOf("\n{", markerEnd);
            if (payloadStart < 0) return "{}";
            String json = input.substring(payloadStart + 1).trim();
            JsonNode request = new ObjectMapper().readTree(json);
            JsonNode page = request.path("page");
            var keyPoints = page.path("keyPoints");
            var covered = new java.util.ArrayList<String>();
            var body = new StringBuilder();
            for (JsonNode point : keyPoints) {
                String value = point.asText().trim();
                if (!value.isBlank()) {
                    covered.add(value);
                    if (!body.isEmpty()) body.append(". ");
                    body.append(value);
                }
            }
            JsonNode sources = request.path("sources");
            var sourceIds = new java.util.ArrayList<String>();
            if (sources.isArray()) {
                for (JsonNode source : sources) {
                    sourceIds.add(source.path("blockId").asText());
                    String text = source.path("text").asText("").trim();
                    if (!text.isBlank() && !body.toString().contains(text)) {
                        if (!body.isEmpty()) body.append(". ");
                        body.append(abbreviate(text, 120));
                    }
                }
            }
            var result = new java.util.LinkedHashMap<String, Object>();
            result.put("title", page.path("title").asText("Generated page"));
            result.put("body", body.isEmpty() ? page.path("objective").asText("Key message") : body.toString());
            result.put("keyPointsCovered", covered);
            result.put("sourceBlockIds", sourceIds);
            return new ObjectMapper().writeValueAsString(result);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String abbreviate(String text, int maxLength) {
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength);
    }
}
