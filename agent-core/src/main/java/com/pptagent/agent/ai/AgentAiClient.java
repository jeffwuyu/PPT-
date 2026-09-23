package com.pptagent.agent.ai;

import com.pptagent.agent.tool.AgentSpringAiTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service
public class AgentAiClient {

    private final ChatClient chatClient;

    public AgentAiClient(ChatModel chatModel, AgentSpringAiTools agentSpringAiTools) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new AgentSystemPromptAdvisor(), new AgentMemoryAdvisor())
                .defaultTools(agentSpringAiTools)
                .build();
    }

    public String generatePlan(String prompt, String memoryContext) {
        return chatClient.prompt()
                .user("PLAN_REQUEST\n" + prompt)
                .advisors(advisor -> advisor.param(AgentMemoryAdvisor.MEMORY_CONTEXT_KEY, memoryContext))
                .call()
                .content();
    }

    public String reactCheckpoint(String checkpointObjective, String memoryContext, boolean forceReplan) {
        String marker = forceReplan ? "REPLAN_CHECKPOINT" : "REACT_CHECKPOINT";
        return chatClient.prompt()
                .user(marker + "\n" + checkpointObjective)
                .advisors(advisor -> advisor.param(AgentMemoryAdvisor.MEMORY_CONTEXT_KEY, memoryContext))
                .call()
                .content();
    }

    public String generatePageContent(String pageInputJson) {
        return chatClient.prompt()
                .user("PAGE_CONTENT_REQUEST\nReturn only JSON with title, body, keyPointsCovered, and sourceBlockIds. "
                        + "Use the approved page and supplied source blocks. Cover every key point without inventing facts.\n"
                        + pageInputJson)
                .call()
                .content();
    }

    public String translatePlan(String markdown) {
        return chatClient.prompt()
                .user("TRANSLATE_PLAN_TO_CHINESE\n" + markdown)
                .call()
                .content();
    }

    public String compressMemory(String content) {
        return chatClient.prompt()
                .user("COMPRESS_MEMORY\n" + content)
                .call()
                .content();
    }
}
