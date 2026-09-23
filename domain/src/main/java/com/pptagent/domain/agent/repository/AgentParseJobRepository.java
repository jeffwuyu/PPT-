package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.agent.AgentParseJobEntity;

import java.util.Optional;

public interface AgentParseJobRepository extends BaseMapper<AgentParseJobEntity> {
    default Optional<AgentParseJobEntity> findByOwnerAndId(Long ownerUserId, Long id) {
        return Optional.ofNullable(selectOne(new QueryWrapper<AgentParseJobEntity>()
                .eq("owner_user_id", ownerUserId).eq("id", id)));
    }

    default Optional<AgentParseJobEntity> findByIdempotency(Long ownerUserId, Long fileAssetId, String key) {
        return Optional.ofNullable(selectOne(new QueryWrapper<AgentParseJobEntity>()
                .eq("owner_user_id", ownerUserId).eq("file_asset_id", fileAssetId)
                .eq("idempotency_key", key)));
    }
}
