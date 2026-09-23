package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.pptagent.domain.agent.AgentJobEntity;
import com.pptagent.domain.agent.AgentJobStatus;

import java.util.Optional;

public interface AgentJobRepository extends BaseMapper<AgentJobEntity> {
    default int claimForGeneration(Long jobId, Long generationJobId) {
        return update(null, new UpdateWrapper<AgentJobEntity>()
                .eq("id", jobId)
                .eq("status", AgentJobStatus.PLANNED)
                .isNull("active_generation_job_id")
                .set("active_generation_job_id", generationJobId));
    }

    default int startGeneration(Long jobId, Long generationJobId) {
        return update(null, new UpdateWrapper<AgentJobEntity>()
                .eq("id", jobId)
                .eq("active_generation_job_id", generationJobId)
                .eq("status", AgentJobStatus.PLANNED)
                .set("status", AgentJobStatus.EXECUTING));
    }

    default int startLegacyExecution(Long jobId) {
        return update(null, new UpdateWrapper<AgentJobEntity>()
                .eq("id", jobId)
                .eq("status", AgentJobStatus.PLANNED)
                .isNull("active_generation_job_id")
                .set("status", AgentJobStatus.EXECUTING));
    }

    default Optional<AgentJobEntity> findByIdAndOwner(Long id, Long ownerUserId) {
        return Optional.ofNullable(selectOne(new QueryWrapper<AgentJobEntity>()
                .eq("id", id)
                .eq("owner_user_id", ownerUserId)));
    }
}
