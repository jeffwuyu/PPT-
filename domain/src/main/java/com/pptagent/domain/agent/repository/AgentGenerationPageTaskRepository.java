package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.agent.AgentGenerationPageTaskEntity;

import java.util.List;
import java.time.Instant;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

public interface AgentGenerationPageTaskRepository extends BaseMapper<AgentGenerationPageTaskEntity> {
    default List<AgentGenerationPageTaskEntity> findByGenerationJobId(Long generationJobId) {
        return selectList(new QueryWrapper<AgentGenerationPageTaskEntity>()
                .eq("generation_job_id", generationJobId).orderByAsc("page_order"));
    }

    default int claimPage(Long id, String leaseToken, Instant leaseExpiresAt) {
        Instant now = Instant.now();
        return update(null, new UpdateWrapper<AgentGenerationPageTaskEntity>()
                .eq("id", id)
                .and(w -> w.eq("status", "PENDING")
                        .and(x -> x.isNull("next_attempt_at").or().le("next_attempt_at", now))
                        .or(x -> x.eq("status", "RUNNING").lt("lease_expires_at", now)))
                .set("status", "RUNNING")
                .set("lease_token", leaseToken)
                .set("lease_expires_at", leaseExpiresAt)
                .set("next_attempt_at", null)
                .setSql("attempts = attempts + 1")
                .set("updated_at", now));
    }

    default List<AgentGenerationPageTaskEntity> findClaimableByGenerationJobId(Long generationJobId) {
        Instant now = Instant.now();
        return selectList(new QueryWrapper<AgentGenerationPageTaskEntity>()
                .eq("generation_job_id", generationJobId)
                .and(w -> w.eq("status", "PENDING")
                        .and(x -> x.isNull("next_attempt_at").or().le("next_attempt_at", now)))
                .orderByAsc("page_order"));
    }

    default List<AgentGenerationPageTaskEntity> findExpiredLeases(int limit) {
        return selectList(new QueryWrapper<AgentGenerationPageTaskEntity>()
                .eq("status", "RUNNING")
                .lt("lease_expires_at", Instant.now())
                .orderByAsc("lease_expires_at")
                .last("limit " + Math.max(1, Math.min(limit, 100))));
    }

    default int failExpiredExhausted(Instant now) {
        return update(null, new UpdateWrapper<AgentGenerationPageTaskEntity>()
                .eq("status", "RUNNING").lt("lease_expires_at", now)
                .ge("attempts", 4)
                .set("status", "FAILED").set("lease_token", null).set("lease_expires_at", null)
                .set("failure_reason", "Page exceeded retry budget").set("updated_at", now));
    }

    default int requeueExpired(Long id, Instant now) {
        return update(null, new UpdateWrapper<AgentGenerationPageTaskEntity>()
                .eq("id", id).eq("status", "RUNNING")
                .lt("lease_expires_at", now)
                .set("status", "PENDING")
                .set("lease_token", null)
                .set("lease_expires_at", null)
                .set("updated_at", now));
    }

    default int releaseClaim(Long id, String leaseToken, Instant now) {
        return update(null, new UpdateWrapper<AgentGenerationPageTaskEntity>()
                .eq("id", id).eq("status", "RUNNING").eq("lease_token", leaseToken)
                .set("status", "PENDING").set("lease_token", null).set("lease_expires_at", null)
                .set("updated_at", now));
    }

    default int renewClaim(Long id, String leaseToken, Instant expiresAt, Instant now) {
        return update(null, new UpdateWrapper<AgentGenerationPageTaskEntity>()
                .eq("id", id).eq("status", "RUNNING").eq("lease_token", leaseToken)
                .set("lease_expires_at", expiresAt).set("updated_at", now));
    }

    default int cancelPending(Long generationJobId, Instant now) {
        return update(null, new UpdateWrapper<AgentGenerationPageTaskEntity>()
                .eq("generation_job_id", generationJobId).eq("status", "PENDING")
                .set("status", "CANCELLED")
                .set("failure_reason", "Generation cancelled before page execution")
                .set("updated_at", now));
    }
}
