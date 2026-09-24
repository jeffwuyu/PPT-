package com.pptagent.api.agent;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.pptagent.domain.agent.AgentGenerationJobEntity;
import com.pptagent.domain.agent.AgentGenerationPageTaskEntity;
import com.pptagent.domain.agent.repository.AgentGenerationJobRepository;
import com.pptagent.domain.agent.repository.AgentGenerationPageTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;

@Service
public class AgentGenerationPageLifecycleService {
    public static final int MAX_ATTEMPTS = 4;

    private final AgentGenerationJobRepository jobs;
    private final AgentGenerationPageTaskRepository pages;

    public AgentGenerationPageLifecycleService(AgentGenerationJobRepository jobs,
                                               AgentGenerationPageTaskRepository pages) {
        this.jobs = jobs;
        this.pages = pages;
    }

    @Transactional
    public boolean complete(Long pageTaskId, Long generationJobId, String leaseToken) {
        AgentGenerationJobEntity generation = jobs.selectById(generationJobId);
        if (generation == null || !("RUNNING".equals(generation.getStatus())
                || "PAUSING".equals(generation.getStatus()))) {
            return false;
        }
        Instant now = Instant.now();
        int pageUpdated = pages.update(null, new UpdateWrapper<AgentGenerationPageTaskEntity>()
                .eq("id", pageTaskId).eq("generation_job_id", generationJobId)
                .eq("status", "RUNNING").eq("lease_token", leaseToken)
                .set("status", "SUCCEEDED").set("lease_token", null).set("lease_expires_at", null)
                .set("failure_reason", null).set("updated_at", now));
        if (pageUpdated != 1) return false;

        int jobUpdated = jobs.update(null, new UpdateWrapper<AgentGenerationJobEntity>()
                .eq("id", generationJobId).eq("revision", generation.getRevision())
                .in("status", "RUNNING", "PAUSING")
                .setSql("completed_pages = completed_pages + 1, revision = revision + 1")
                .set("updated_at", now));
        if (jobUpdated != 1) throw new IllegalStateException("Generation changed while completing page");
        return true;
    }

    @Transactional
    public boolean failOrRetry(Long pageTaskId, Long generationJobId, String leaseToken, String reason) {
        AgentGenerationPageTaskEntity current = pages.selectById(pageTaskId);
        if (current == null || !"RUNNING".equals(current.getStatus())
                || !leaseToken.equals(current.getLeaseToken())) return false;
        boolean terminal = current.getAttempts() >= MAX_ATTEMPTS;
        Instant now = Instant.now();
        Instant retryAt = terminal ? null : now.plusSeconds(Math.min(60, 2L << Math.max(0, current.getAttempts() - 1)));
        int updated = pages.update(null, new UpdateWrapper<AgentGenerationPageTaskEntity>()
                .eq("id", pageTaskId).eq("generation_job_id", generationJobId)
                .eq("status", "RUNNING").eq("lease_token", leaseToken)
                .set("status", terminal ? "FAILED" : "PENDING")
                .set("lease_token", null).set("lease_expires_at", null)
                .set("next_attempt_at", retryAt).set("failure_reason", reason)
                .set("updated_at", now));
        return updated == 1 && terminal;
    }
}
