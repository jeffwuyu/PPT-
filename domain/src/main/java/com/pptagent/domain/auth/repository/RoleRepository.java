package com.pptagent.domain.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.auth.RoleEntity;

import java.util.Optional;

public interface RoleRepository extends BaseMapper<RoleEntity> {

    default Optional<RoleEntity> findByCode(String code) {
        return Optional.ofNullable(selectOne(new QueryWrapper<RoleEntity>().eq("code", code)));
    }
}
