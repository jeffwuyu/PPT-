package com.pptagent.agent.ai;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

public class AgentSystemPromptAdvisor implements CallAdvisor {

    @Override
    public String getName() {
        return "agent-system-prompt-advisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        String systemPrompt = """
                You are a PPT generation agent using a plan-execution workflow.
                Always produce concrete, checkpoint-oriented work.
                Persisted Markdown plans are the source of truth for recovery.
                Use tools only through validated tool calls, and keep slide JSON consistent with revised plans.
                Do not use RAG, vector search, or image tag embedding retrieval.
                """;
        return callAdvisorChain.nextCall(chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().augmentSystemMessage(systemPrompt))
                .build());
    }
}
