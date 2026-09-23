package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.agent.AgentGenerationJobEntity;

import java.util.Optional;

public interface AgentGenerationJobRepository extends BaseMapper<AgentGenerationJobEntity> {
    default Optional<AgentGenerationJobEntity> findByOwnerAndId(Long ownerUserId, Long id) {
        return Optional.ofNullable(selectOne(new QueryWrapper<AgentGenerationJobEntity>()
                .eq("owner_user_id", ownerUserId).eq("id", id)));
    }

    default Optional<AgentGenerationJobEntity> findByIdempotency(Long ownerUserId, Long agentJobId, String key) {
        return Optional.ofNullable(selectOne(new QueryWrapper<AgentGenerationJobEntity>()
                .eq("owner_user_id", ownerUserId).eq("agent_job_id", agentJobId).eq("idempotency_key", key)));
    }
}
