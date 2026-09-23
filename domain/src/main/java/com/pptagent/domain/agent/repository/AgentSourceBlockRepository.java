package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.agent.AgentSourceBlockEntity;

import java.util.List;

public interface AgentSourceBlockRepository extends BaseMapper<AgentSourceBlockEntity> {
    default List<AgentSourceBlockEntity> findByParseJobId(Long parseJobId) {
        return selectList(new QueryWrapper<AgentSourceBlockEntity>()
                .eq("parse_job_id", parseJobId).orderByAsc("id"));
    }

    default int deleteByParseJobId(Long parseJobId) {
        return delete(new QueryWrapper<AgentSourceBlockEntity>().eq("parse_job_id", parseJobId));
    }
}
