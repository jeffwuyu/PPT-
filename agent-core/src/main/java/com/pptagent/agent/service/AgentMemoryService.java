package com.pptagent.agent.service;

import com.pptagent.agent.ai.AgentAiClient;
import com.pptagent.agent.config.AgentProperties;
import com.pptagent.domain.agent.AgentMemoryEntity;
import com.pptagent.domain.agent.AgentMemoryScope;
import com.pptagent.domain.agent.repository.AgentMemoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentMemoryService {

    private final AgentMemoryRepository memoryRepository;
    private final AgentAiClient aiClient;
    private final AgentProperties agentProperties;

    public AgentMemoryService(AgentMemoryRepository memoryRepository, AgentAiClient aiClient, AgentProperties agentProperties) {
        this.memoryRepository = memoryRepository;
        this.aiClient = aiClient;
        this.agentProperties = agentProperties;
    }

    @Transactional
    public void append(Long jobId, Long presentationId, Long userId, AgentMemoryScope scope, String kind, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        memoryRepository.insert(new AgentMemoryEntity(jobId, presentationId, userId, scope, kind, content, false));
        compressIfNeeded(jobId, presentationId, userId, scope);
    }

    @Transactional(readOnly = true)
    public String buildMemoryContext(Long jobId) {
        List<AgentMemoryEntity> shortTerm = memoryRepository.findActiveByJobAndScope(jobId, AgentMemoryScope.SHORT_TERM);
        List<AgentMemoryEntity> longTerm = memoryRepository.findActiveByJobAndScope(jobId, AgentMemoryScope.LONG_TERM);
        return """
                Short-term:
                %s

                Long-term:
                %s
                """.formatted(join(shortTerm), join(longTerm));
    }

    @Transactional(readOnly = true)
    public List<AgentMemoryEntity> list(Long jobId) {
        return memoryRepository.findByJobId(jobId);
    }

    private void compressIfNeeded(Long jobId, Long presentationId, Long userId, AgentMemoryScope scope) {
        List<AgentMemoryEntity> active = memoryRepository.findActiveByJobAndScope(jobId, scope);
        int totalLength = active.stream().mapToInt(memory -> memory.getContentText().length()).sum();
        if (totalLength <= agentProperties.memoryCompressionThresholdChars()) {
            return;
        }
        String summary = aiClient.compressMemory(join(active));
        for (AgentMemoryEntity memory : active) {
            memory.archive();
            memoryRepository.updateById(memory);
        }
        memoryRepository.insert(new AgentMemoryEntity(jobId, presentationId, userId, scope, "SUMMARY", summary, true));
    }

    private String join(List<AgentMemoryEntity> memories) {
        return memories.stream()
                .map(memory -> "- " + memory.getMemoryKind() + ": " + memory.getContentText())
                .collect(Collectors.joining("\n"));
    }
}
