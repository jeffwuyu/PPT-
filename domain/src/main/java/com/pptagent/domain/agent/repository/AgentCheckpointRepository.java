package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.agent.AgentCheckpointEntity;
import com.pptagent.domain.agent.AgentCheckpointStatus;

import java.util.List;

public interface AgentCheckpointRepository extends BaseMapper<AgentCheckpointEntity> {

    default List<AgentCheckpointEntity> findByJobIdOrderByCheckpointOrder(Long jobId) {
        return selectList(new QueryWrapper<AgentCheckpointEntity>().eq("job_id", jobId).orderByAsc("checkpoint_order"));
    }

    default boolean existsForJob(Long jobId) {
        return selectCount(new QueryWrapper<AgentCheckpointEntity>().eq("job_id", jobId)) > 0;
    }

    default List<AgentCheckpointEntity> findRunnableByJobId(Long jobId) {
        return selectList(new QueryWrapper<AgentCheckpointEntity>()
                .eq("job_id", jobId)
                .in("status", AgentCheckpointStatus.PENDING, AgentCheckpointStatus.RUNNING)
                .orderByAsc("checkpoint_order"));
    }
}
