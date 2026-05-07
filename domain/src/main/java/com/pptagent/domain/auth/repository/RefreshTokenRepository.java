package com.pptagent.domain.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.auth.RefreshTokenEntity;

import java.util.Optional;

public interface RefreshTokenRepository extends BaseMapper<RefreshTokenEntity> {

    default Optional<RefreshTokenEntity> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(selectOne(new QueryWrapper<RefreshTokenEntity>().eq("token_hash", tokenHash)));
    }
}
