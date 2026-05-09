package com.pptagent.agent.ai;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

public class AgentMemoryAdvisor implements CallAdvisor {

    public static final String MEMORY_CONTEXT_KEY = "agent_memory_context";

    @Override
    public String getName() {
        return "agent-memory-advisor";
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        Object memory = chatClientRequest.context().get(MEMORY_CONTEXT_KEY);
        if (memory == null || memory.toString().isBlank()) {
            return callAdvisorChain.nextCall(chatClientRequest);
        }
        String memoryPrompt = "\nRelevant short-term and long-term memory:\n" + memory;
        return callAdvisorChain.nextCall(chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().augmentSystemMessage(memoryPrompt))
                .build());
    }
}
