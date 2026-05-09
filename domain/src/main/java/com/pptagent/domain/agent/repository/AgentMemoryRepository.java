package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.agent.AgentMemoryEntity;
import com.pptagent.domain.agent.AgentMemoryScope;

import java.util.List;

public interface AgentMemoryRepository extends BaseMapper<AgentMemoryEntity> {

    default List<AgentMemoryEntity> findActiveByJobAndScope(Long jobId, AgentMemoryScope scope) {
        return selectList(new QueryWrapper<AgentMemoryEntity>()
                .eq("job_id", jobId)
                .eq("memory_scope", scope)
                .eq("archived", false)
                .orderByAsc("id"));
    }

    default List<AgentMemoryEntity> findByJobId(Long jobId) {
        return selectList(new QueryWrapper<AgentMemoryEntity>().eq("job_id", jobId).orderByAsc("id"));
    }
}
