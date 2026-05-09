package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.agent.AgentJobEntity;

import java.util.Optional;

public interface AgentJobRepository extends BaseMapper<AgentJobEntity> {

    default Optional<AgentJobEntity> findByIdAndOwner(Long id, Long ownerUserId) {
        return Optional.ofNullable(selectOne(new QueryWrapper<AgentJobEntity>()
                .eq("id", id)
                .eq("owner_user_id", ownerUserId)));
    }
}
