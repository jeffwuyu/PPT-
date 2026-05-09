package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.agent.AgentMessageEntity;

import java.util.List;

public interface AgentMessageRepository extends BaseMapper<AgentMessageEntity> {

    default List<AgentMessageEntity> findRecentByJobId(Long jobId, int limit) {
        return selectList(new QueryWrapper<AgentMessageEntity>()
                .eq("job_id", jobId)
                .orderByDesc("id")
                .last("limit " + Math.max(1, limit)));
    }

    default List<AgentMessageEntity> findByJobIdOrderByIdAsc(Long jobId) {
        return selectList(new QueryWrapper<AgentMessageEntity>()
                .eq("job_id", jobId)
                .orderByAsc("id"));
    }
}
