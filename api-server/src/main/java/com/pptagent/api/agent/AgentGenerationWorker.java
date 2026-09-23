package com.pptagent.api.agent;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.pptagent.agent.dto.AgentResponse;
import com.pptagent.agent.service.AgentJobService;
import com.pptagent.domain.agent.AgentGenerationJobEntity;
import com.pptagent.domain.agent.AgentGenerationPageTaskEntity;
import com.pptagent.domain.agent.AgentJobStatus;
import com.pptagent.domain.agent.repository.AgentGenerationJobRepository;
import com.pptagent.domain.agent.repository.AgentGenerationPageTaskRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AgentGenerationWorker {
    private final AgentGenerationJobRepository generationJobs;
    private final AgentGenerationPageTaskRepository pageTasks;
    private final AgentJobService agentJobService;

    public AgentGenerationWorker(AgentGenerationJobRepository generationJobs,
                                 AgentGenerationPageTaskRepository pageTasks,
                                 AgentJobService agentJobService) {
        this.generationJobs = generationJobs;
        this.pageTasks = pageTasks;
        this.agentJobService = agentJobService;
    }

    @Async
    public void execute(Long generationJobId, Long ownerUserId, Long agentJobId) {
        if (transition(generationJobId, ownerUserId, "QUEUED", "RUNNING") != 1) return;
        for (AgentGenerationPageTaskEntity page : pageTasks.findByGenerationJobId(generationJobId)) {
            if (transitionPage(page.getId(), "PENDING", "RUNNING") != 1) continue;
            try {
                AgentResponse result = agentJobService.executeGenerationPage(
                        ownerUserId, agentJobId, generationJobId, page.getPageOrder() + 1);
                if (result.status() == AgentJobStatus.FAILED) {
                    completePage(page.getId(), "FAILED", result.failureReason());
                    finish(generationJobId, ownerUserId, "FAILED", result.failureReason());
                    return;
                }
                completePage(page.getId(), "SUCCEEDED", null);
                incrementCompleted(generationJobId, ownerUserId);
            } catch (RuntimeException exception) {
                completePage(page.getId(), "FAILED", exception.getMessage());
                finish(generationJobId, ownerUserId, "FAILED", exception.getMessage());
                return;
            }
        }
        AgentGenerationJobEntity current = generationJobs.selectById(generationJobId);
        if (current != null && current.getCompletedPages() == current.getTotalPages()) {
            try {
                agentJobService.completeGeneration(ownerUserId, agentJobId, generationJobId);
                finish(generationJobId, ownerUserId, "SUCCEEDED", null);
            } catch (RuntimeException exception) {
                finish(generationJobId, ownerUserId, "FAILED", exception.getMessage());
            }
        }
    }

    @Transactional
    int transition(Long id, Long owner, String expected, String next) {
        return generationJobs.update(null, new UpdateWrapper<AgentGenerationJobEntity>()
                .eq("id", id).eq("owner_user_id", owner).eq("status", expected)
                .set("status", next).setSql("revision = revision + 1")
                .set("updated_at", Instant.now()));
    }

    @Transactional
    void finish(Long id, Long owner, String status, String failureReason) {
        int updated = generationJobs.update(null, new UpdateWrapper<AgentGenerationJobEntity>()
                .eq("id", id).eq("owner_user_id", owner).eq("status", "RUNNING")
                .set("status", status).set("failure_reason", failureReason)
                .setSql("revision = revision + 1")
                .set("updated_at", Instant.now()));
    }

    @Transactional
    int transitionPage(Long id, String expected, String next) {
        return pageTasks.update(null, new UpdateWrapper<AgentGenerationPageTaskEntity>()
                .eq("id", id).eq("status", expected).set("status", next)
                .setSql("attempts = attempts + 1").set("updated_at", Instant.now()));
    }

    @Transactional
    void completePage(Long id, String status, String failureReason) {
        pageTasks.update(null, new UpdateWrapper<AgentGenerationPageTaskEntity>()
                .eq("id", id).eq("status", "RUNNING").set("status", status)
                .set("failure_reason", failureReason).set("updated_at", Instant.now()));
    }

    @Transactional
    void incrementCompleted(Long id, Long owner) {
        generationJobs.update(null, new UpdateWrapper<AgentGenerationJobEntity>()
                .eq("id", id).eq("owner_user_id", owner).eq("status", "RUNNING")
                .setSql("completed_pages = completed_pages + 1, revision = revision + 1")
                .set("updated_at", Instant.now()));
    }
}
