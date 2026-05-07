package com.pptagent.domain.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.auth.UserLevelEntity;

import java.util.Optional;

public interface UserLevelRepository extends BaseMapper<UserLevelEntity> {

    default Optional<UserLevelEntity> findByCode(String code) {
        return Optional.ofNullable(selectOne(new QueryWrapper<UserLevelEntity>().eq("code", code)));
    }
}
