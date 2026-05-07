package com.pptagent.domain.auth;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("permissions")
public class PermissionEntity {

    @TableId
    private Long id;
    private String code;
    private String description;
    private Instant createdAt;

    protected PermissionEntity() {
    }

    public PermissionEntity(String code, String description) {
        this.code = code;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }
}
