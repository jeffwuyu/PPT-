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
                        .or(x -> x.eq("status", "RUNNING").lt("lease_expires_at", now)))
                .set("status", "RUNNING")
                .set("lease_token", leaseToken)
                .set("lease_expires_at", leaseExpiresAt)
                .setSql("attempts = attempts + 1")
                .set("updated_at", now));
    }

    default List<AgentGenerationPageTaskEntity> findExpiredLeases(int limit) {
        return selectList(new QueryWrapper<AgentGenerationPageTaskEntity>()
                .eq("status", "RUNNING")
                .lt("lease_expires_at", Instant.now())
                .orderByAsc("lease_expires_at")
                .last("limit " + Math.max(1, Math.min(limit, 100))));
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
}
