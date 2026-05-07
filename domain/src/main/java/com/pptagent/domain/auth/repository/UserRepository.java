package com.pptagent.domain.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.auth.UserEntity;

import java.util.Optional;

public interface UserRepository extends BaseMapper<UserEntity> {

    default boolean existsByEmail(String email) {
        return selectCount(new QueryWrapper<UserEntity>().eq("email", email)) > 0;
    }

    default boolean existsByUsername(String username) {
        return selectCount(new QueryWrapper<UserEntity>().eq("username", username)) > 0;
    }

    default Optional<UserEntity> findByEmail(String email) {
        return Optional.ofNullable(selectOne(new QueryWrapper<UserEntity>().eq("email", email)));
    }

    default Optional<UserEntity> findWithRolesById(Long id) {
        return Optional.ofNullable(selectById(id));
    }
}
