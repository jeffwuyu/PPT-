package com.pptagent.domain.auth;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("teams")
public class TeamEntity {

    @TableId
    private Long id;
    private String name;
    private Long ownerUserId;
    private Instant createdAt;

    @TableField(exist = false)
    private UserEntity owner;

    protected TeamEntity() {
    }

    public TeamEntity(String name, UserEntity owner) {
        this.name = name;
        this.owner = owner;
        this.ownerUserId = owner.getId();
        this.createdAt = Instant.now();
    }
}
