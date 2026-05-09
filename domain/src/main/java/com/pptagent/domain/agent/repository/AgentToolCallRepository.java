package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.agent.AgentToolCallEntity;

import java.util.List;

public interface AgentToolCallRepository extends BaseMapper<AgentToolCallEntity> {

    default List<AgentToolCallEntity> findByJobId(Long jobId) {
        return selectList(new QueryWrapper<AgentToolCallEntity>().eq("job_id", jobId).orderByAsc("id"));
    }
}
