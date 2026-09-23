package com.pptagent.api.agent;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.api.agent.dto.GenerationJobResponse;
import com.pptagent.api.agent.dto.GenerationSubmissionRequest;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.agent.AgentCheckpointEntity;
import com.pptagent.domain.agent.AgentGenerationJobEntity;
import com.pptagent.domain.agent.AgentGenerationPageTaskEntity;
import com.pptagent.domain.agent.AgentJobEntity;
import com.pptagent.domain.agent.AgentPlanEntity;
import com.pptagent.domain.agent.repository.AgentCheckpointRepository;
import com.pptagent.domain.agent.repository.AgentGenerationJobRepository;
import com.pptagent.domain.agent.repository.AgentGenerationPageTaskRepository;
import com.pptagent.domain.agent.repository.AgentJobRepository;
import com.pptagent.domain.agent.repository.AgentPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;

@Service
public class AgentGenerationService {
    private final AgentGenerationJobRepository generationJobs;
    private final AgentGenerationPageTaskRepository pageTasks;
    private final AgentJobRepository agentJobs;
    private final AgentPlanRepository plans;
    private final AgentCheckpointRepository checkpoints;
    private final AgentGenerationWorker worker;
    private final ObjectMapper objectMapper;

    public AgentGenerationService(AgentGenerationJobRepository generationJobs,
                                  AgentGenerationPageTaskRepository pageTasks,
                                  AgentJobRepository agentJobs,
                                  AgentPlanRepository plans,
                                  AgentCheckpointRepository checkpoints,
                                  AgentGenerationWorker worker,
                                  ObjectMapper objectMapper) {
        this.generationJobs = generationJobs;
        this.pageTasks = pageTasks;
        this.agentJobs = agentJobs;
        this.plans = plans;
        this.checkpoints = checkpoints;
        this.worker = worker;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GenerationJobResponse submit(Long ownerUserId, Long agentJobId, GenerationSubmissionRequest request) {
        AgentJobEntity agentJob = agentJobs.findByIdAndOwner(agentJobId, ownerUserId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Agent job not found"));
        var existing = generationJobs.findByIdempotency(ownerUserId, agentJobId, request.idempotencyKey());
        if (existing.isPresent()) return response(existing.get());

        AgentPlanEntity plan = plans.findLatestByJobId(agentJobId)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "Agent plan must be approved before generation"));
        if (plan.getApprovedOutlineRevision() == null
                || plan.getApprovedOutlineRevision() != plan.getOutlineRevision()
                || !checkpoints.existsForJob(agentJobId)) {
            throw new PlatformException(ErrorCode.CONFLICT, "Current outline revision must be approved before generation");
        }
        List<Page> pages = pages(plan.getApprovedOutlineJson());
        if (pages.isEmpty()) throw new PlatformException(ErrorCode.CONFLICT, "Approved outline has no pages");

        AgentGenerationJobEntity generation = new AgentGenerationJobEntity(agentJobId, ownerUserId,
                agentJob.getPresentationId(), request.idempotencyKey(), plan.getApprovedOutlineRevision(), pages.size());
        generationJobs.insert(generation);
        if (agentJobs.claimForGeneration(agentJobId, generation.getId()) != 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "Agent job already has an active generation or has started through the legacy executor");
        }
        for (Page page : pages) pageTasks.insert(new AgentGenerationPageTaskEntity(generation.getId(), page.id(), page.order()));
        Long generationId = generation.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    worker.execute(generationId, ownerUserId, agentJobId);
                } catch (RejectedExecutionException ignored) {
                    // The scheduled durable dispatcher will retry this QUEUED row.
                }
            }
        });
        return response(generation);
    }

    @Transactional(readOnly = true)
    public GenerationJobResponse get(Long ownerUserId, Long generationJobId) {
        AgentGenerationJobEntity generation = generationJobs.findByOwnerAndId(ownerUserId, generationJobId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Generation job not found"));
        return response(generation);
    }

    @Transactional
    public GenerationJobResponse pause(Long ownerUserId, Long generationJobId) {
        AgentGenerationJobEntity job = owned(ownerUserId, generationJobId);
        if ("PAUSED".equals(job.getStatus()) || "PAUSING".equals(job.getStatus())) return response(job);
        if ("QUEUED".equals(job.getStatus())) updateStatus(job, "QUEUED", "PAUSED");
        else if ("RUNNING".equals(job.getStatus())) updateStatus(job, "RUNNING", "PAUSING");
        else throw new PlatformException(ErrorCode.CONFLICT, "Generation cannot be paused in its current state");
        return get(ownerUserId, generationJobId);
    }

    @Transactional
    public GenerationJobResponse cancel(Long ownerUserId, Long generationJobId) {
        AgentGenerationJobEntity job = owned(ownerUserId, generationJobId);
        if ("CANCELLED".equals(job.getStatus())) return response(job);
        if ("QUEUED".equals(job.getStatus())) {
            updateStatus(job, "QUEUED", "CANCELLED");
            pageTasks.cancelPending(job.getId(), Instant.now());
        } else if ("RUNNING".equals(job.getStatus()) || "PAUSING".equals(job.getStatus())) {
            updateStatus(job, job.getStatus(), "CANCELLING");
        } else {
            throw new PlatformException(ErrorCode.CONFLICT, "Generation cannot be cancelled in its current state");
        }
        return get(ownerUserId, generationJobId);
    }

    @Transactional
    public GenerationJobResponse resume(Long ownerUserId, Long generationJobId) {
        AgentGenerationJobEntity job = owned(ownerUserId, generationJobId);
        if (!"PAUSED".equals(job.getStatus())) {
            throw new PlatformException(ErrorCode.CONFLICT, "Only paused generations can be resumed");
        }
        updateStatus(job, "PAUSED", "QUEUED");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    worker.execute(job.getId(), ownerUserId, job.getAgentJobId());
                } catch (RejectedExecutionException ignored) {
                    // The scheduled durable dispatcher will retry this QUEUED row.
                }
            }
        });
        return get(ownerUserId, generationJobId);
    }

    private AgentGenerationJobEntity owned(Long ownerUserId, Long id) {
        return generationJobs.findByOwnerAndId(ownerUserId, id)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Generation job not found"));
    }

    private void updateStatus(AgentGenerationJobEntity job, String expected, String next) {
        int updated = generationJobs.update(null, new UpdateWrapper<AgentGenerationJobEntity>()
                .eq("id", job.getId()).eq("owner_user_id", job.getOwnerUserId()).eq("status", expected)
                .set("status", next).setSql("revision = revision + 1").set("updated_at", Instant.now()));
        if (updated != 1) throw new PlatformException(ErrorCode.CONFLICT, "Generation status changed; reload and retry");
    }

    private GenerationJobResponse response(AgentGenerationJobEntity job) {
        List<GenerationJobResponse.PageTaskResponse> pages = pageTasks.findByGenerationJobId(job.getId()).stream()
                .map(task -> new GenerationJobResponse.PageTaskResponse(task.getPageId(), task.getPageOrder(),
                        task.getStatus(), task.getAttempts(), task.getFailureReason()))
                .toList();
        return new GenerationJobResponse(job.getId(), job.getAgentJobId(), job.getStatus(), job.getRevision(),
                job.getApprovedOutlineRevision(), job.getTotalPages(), job.getCompletedPages(),
                job.getFailureReason(), pages, job.getCreatedAt(), job.getUpdatedAt());
    }

    private List<Page> pages(String outlineJson) {
        try {
            JsonNode root = objectMapper.readTree(outlineJson);
            JsonNode array = root.path("pages");
            List<Page> pages = new ArrayList<>();
            int order = 0;
            for (JsonNode page : array) {
                String id = page.path("pageId").asText("");
                if (id.isBlank()) throw new PlatformException(ErrorCode.CONFLICT, "Approved outline contains a page without pageId");
                pages.add(new Page(id, order++));
            }
            return pages;
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.CONFLICT, "Approved outline is invalid");
        }
    }

    private record Page(String id, int order) { }
}
