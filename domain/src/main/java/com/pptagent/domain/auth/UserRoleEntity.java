package com.pptagent.domain.auth;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_roles")
public class UserRoleEntity {

    @TableId
    private Long userId;
    private Long roleId;

    protected UserRoleEntity() {
    }

    public UserRoleEntity(Long userId, Long roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRoleId() {
        return roleId;
    }
}
