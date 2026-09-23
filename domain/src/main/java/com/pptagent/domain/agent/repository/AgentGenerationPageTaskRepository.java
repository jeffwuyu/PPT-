package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.agent.AgentGenerationPageTaskEntity;

import java.util.List;

public interface AgentGenerationPageTaskRepository extends BaseMapper<AgentGenerationPageTaskEntity> {
    default List<AgentGenerationPageTaskEntity> findByGenerationJobId(Long generationJobId) {
        return selectList(new QueryWrapper<AgentGenerationPageTaskEntity>()
                .eq("generation_job_id", generationJobId).orderByAsc("page_order"));
    }
}
