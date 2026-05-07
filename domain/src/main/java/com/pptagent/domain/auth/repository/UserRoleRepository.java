package com.pptagent.domain.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.auth.UserRoleEntity;

import java.util.List;

public interface UserRoleRepository extends BaseMapper<UserRoleEntity> {

    default List<UserRoleEntity> findByUserId(Long userId) {
        return selectList(new QueryWrapper<UserRoleEntity>().eq("user_id", userId));
    }
}
