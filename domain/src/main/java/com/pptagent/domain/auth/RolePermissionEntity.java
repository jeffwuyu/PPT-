package com.pptagent.domain.auth;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("role_permissions")
public class RolePermissionEntity {

    @TableId
    private Long roleId;
    private Long permissionId;

    protected RolePermissionEntity() {
    }

    public RolePermissionEntity(Long roleId, Long permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public Long getPermissionId() {
        return permissionId;
    }
}
