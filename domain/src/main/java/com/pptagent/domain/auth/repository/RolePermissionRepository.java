package com.pptagent.domain.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.auth.RolePermissionEntity;

import java.util.Collection;
import java.util.List;

public interface RolePermissionRepository extends BaseMapper<RolePermissionEntity> {

    default List<RolePermissionEntity> findByRoleIds(Collection<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return selectList(new QueryWrapper<RolePermissionEntity>().in("role_id", roleIds));
    }
}
