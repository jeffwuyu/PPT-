package com.pptagent.api.agent;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.pptagent.agent.service.AgentJobService;
import com.pptagent.domain.agent.AgentGenerationJobEntity;
import com.pptagent.domain.agent.AgentGenerationPageTaskEntity;
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
        try {
            var response = agentJobService.execute(ownerUserId, agentJobId);
            String status = response.status().name().equals("GENERATED") ? "SUCCEEDED" : "FAILED";
            finish(generationJobId, ownerUserId, status,
                    status.equals("FAILED") ? response.failureReason() : null);
        } catch (RuntimeException exception) {
            finish(generationJobId, ownerUserId, "FAILED", exception.getMessage());
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
                .set("completed_pages", "SUCCEEDED".equals(status)
                        ? generationJobs.selectById(id).getTotalPages() : 0)
                .setSql("revision = revision + 1")
                .set("updated_at", Instant.now()));
        if (updated == 1) {
            pageTasks.update(null, new UpdateWrapper<AgentGenerationPageTaskEntity>()
                    .eq("generation_job_id", id).eq("status", "PENDING")
                    .set("status", "SUCCEEDED".equals(status) ? "SUCCEEDED" : "FAILED")
                    .set("failure_reason", failureReason).set("updated_at", Instant.now()));
        }
    }
}
