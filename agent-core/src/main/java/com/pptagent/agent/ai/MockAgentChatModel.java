package com.pptagent.agent.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "ppt.agent", name = "mock-llm-enabled", havingValue = "true", matchIfMissing = true)
public class MockAgentChatModel implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
        String input = prompt.getContents();
        String content;
        if (input.contains("COMPRESS_MEMORY")) {
            content = "Compressed memory: " + abbreviate(input.replace("COMPRESS_MEMORY", ""), 180);
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

    private String abbreviate(String text, int maxLength) {
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength);
    }
}
