package com.pptagent.domain.auth;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("audit_logs")
public class AuditLogEntity {

    @TableId
    private Long id;
    private Long userId;
    private String action;
    private String resourceType;
    private String resourceId;
    private String detail;
    private Instant createdAt;

    @TableField(exist = false)
    private UserEntity user;

    protected AuditLogEntity() {
    }

    public AuditLogEntity(UserEntity user, String action, String resourceType, String resourceId, String detail) {
        this.user = user;
        this.userId = user == null ? null : user.getId();
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.detail = detail;
        this.createdAt = Instant.now();
    }
}
