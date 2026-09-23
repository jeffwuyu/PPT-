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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;
import java.util.UUID;
import java.time.Duration;

@Service
public class AgentGenerationWorker {
    private final AgentGenerationJobRepository generationJobs;
    private final AgentGenerationPageTaskRepository pageTasks;
    private final AgentJobService agentJobService;
    private final TaskExecutor taskExecutor;

    public AgentGenerationWorker(AgentGenerationJobRepository generationJobs,
                                 AgentGenerationPageTaskRepository pageTasks,
                                 AgentJobService agentJobService,
                                 TaskExecutor taskExecutor) {
        this.generationJobs = generationJobs;
        this.pageTasks = pageTasks;
        this.agentJobService = agentJobService;
        this.taskExecutor = taskExecutor;
    }

    @Async
    public void execute(Long generationJobId, Long ownerUserId, Long agentJobId) {
        if (!ensureRunning(generationJobId, ownerUserId)) return;
        for (AgentGenerationPageTaskEntity page : pageTasks.findByGenerationJobId(generationJobId)) {
            AgentGenerationJobEntity control = generationJobs.selectById(generationJobId);
            if (control == null) return;
            if ("PAUSING".equals(control.getStatus())) {
                if (control.getCompletedPages() == control.getTotalPages()) {
                    completeGenerationIfOwned(control);
                } else {
                    setGenerationStatus(control.getId(), control.getOwnerUserId(), "PAUSING", "PAUSED");
                }
                return;
            }
            if ("CANCELLING".equals(control.getStatus())) {
                pageTasks.cancelPending(generationJobId, Instant.now());
                setGenerationStatus(control.getId(), control.getOwnerUserId(), "CANCELLING", "CANCELLED");
                return;
            }
            String leaseToken = UUID.randomUUID().toString();
            if (pageTasks.claimPage(page.getId(), leaseToken, Instant.now().plus(Duration.ofSeconds(30))) != 1) continue;
            try {
                AgentResponse result = agentJobService.executeGenerationPage(
                        ownerUserId, agentJobId, generationJobId, page.getPageOrder() + 1);
                if (result.status() == AgentJobStatus.FAILED) {
                    completePage(page.getId(), leaseToken, "FAILED", result.failureReason());
                    finish(generationJobId, ownerUserId, "FAILED", result.failureReason());
                    return;
                }
                completePage(page.getId(), leaseToken, "SUCCEEDED", null);
                incrementCompleted(generationJobId, ownerUserId);
            } catch (RuntimeException exception) {
                completePage(page.getId(), leaseToken, "FAILED", exception.getMessage());
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

    private void completeGenerationIfOwned(AgentGenerationJobEntity job) {
        try {
            agentJobService.completeGeneration(job.getOwnerUserId(), job.getAgentJobId(), job.getId());
            finish(job.getId(), job.getOwnerUserId(), "SUCCEEDED", null);
        } catch (RuntimeException exception) {
            finish(job.getId(), job.getOwnerUserId(), "FAILED", exception.getMessage());
        }
    }

    @Transactional
    void setGenerationStatus(Long id, Long owner, String expected, String next) {
        generationJobs.update(null, new UpdateWrapper<AgentGenerationJobEntity>()
                .eq("id", id).eq("owner_user_id", owner).eq("status", expected)
                .set("status", next).setSql("revision = revision + 1")
                .set("updated_at", Instant.now()));
    }

    /** Durable queue pump; the conditional QUEUED->RUNNING transition is
     * safe when more than one application instance polls the same database. */
    @Scheduled(fixedDelayString = "${ppt.agent.generation.dispatch-interval-ms:250}")
    public void dispatchQueued() {
        for (AgentGenerationJobEntity job : generationJobs.findQueued(20)) {
            try {
                taskExecutor.execute(() -> execute(job.getId(), job.getOwnerUserId(), job.getAgentJobId()));
            } catch (RejectedExecutionException ignored) {
                return;
            }
        }
    }

    @Scheduled(fixedDelayString = "${ppt.agent.generation.lease-scan-interval-ms:1000}")
    public void recoverExpiredPages() {
        Instant now = Instant.now();
        for (AgentGenerationPageTaskEntity page : pageTasks.findExpiredLeases(50)) {
            if (pageTasks.requeueExpired(page.getId(), now) != 1) continue;
            AgentGenerationJobEntity generation = generationJobs.selectById(page.getGenerationJobId());
            if (generation == null || !("RUNNING".equals(generation.getStatus())
                    || "PAUSING".equals(generation.getStatus())
                    || "CANCELLING".equals(generation.getStatus()))) continue;
            try {
                taskExecutor.execute(() -> execute(generation.getId(), generation.getOwnerUserId(), generation.getAgentJobId()));
            } catch (RejectedExecutionException ignored) {
                return;
            }
        }
    }

    @Transactional
    boolean ensureRunning(Long id, Long owner) {
        int updated = generationJobs.update(null, new UpdateWrapper<AgentGenerationJobEntity>()
                .eq("id", id).eq("owner_user_id", owner).eq("status", "QUEUED")
                .set("status", "RUNNING").setSql("revision = revision + 1")
                .set("updated_at", Instant.now()));
        if (updated == 1) return true;
        AgentGenerationJobEntity current = generationJobs.selectById(id);
        return current != null && owner.equals(current.getOwnerUserId())
                && ("RUNNING".equals(current.getStatus())
                || "PAUSING".equals(current.getStatus())
                || "CANCELLING".equals(current.getStatus()));
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
    void completePage(Long id, String leaseToken, String status, String failureReason) {
        pageTasks.update(null, new UpdateWrapper<AgentGenerationPageTaskEntity>()
                .eq("id", id).eq("status", "RUNNING").eq("lease_token", leaseToken).set("status", status)
                .set("lease_token", null).set("lease_expires_at", null)
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
