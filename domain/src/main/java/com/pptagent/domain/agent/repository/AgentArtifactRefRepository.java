package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.agent.AgentArtifactRefEntity;

import java.util.List;

public interface AgentArtifactRefRepository extends BaseMapper<AgentArtifactRefEntity> {

    default List<AgentArtifactRefEntity> findActiveByCheckpoint(Long checkpointId) {
        return selectList(new QueryWrapper<AgentArtifactRefEntity>()
                .eq("checkpoint_id", checkpointId)
                .eq("active", true)
                .orderByAsc("id"));
    }
}
