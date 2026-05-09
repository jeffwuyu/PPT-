package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.agent.AgentSourceEntity;

import java.util.List;

public interface AgentSourceRepository extends BaseMapper<AgentSourceEntity> {

    default List<AgentSourceEntity> findByJobId(Long jobId) {
        return selectList(new QueryWrapper<AgentSourceEntity>().eq("job_id", jobId).orderByAsc("id"));
    }
}
