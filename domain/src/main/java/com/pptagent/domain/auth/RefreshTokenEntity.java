package com.pptagent.domain.auth;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("refresh_tokens")
public class RefreshTokenEntity {

    @TableId
    private Long id;
    private Long userId;
    private String tokenHash;
    private TokenStatus status;
    private Instant expiresAt;
    private Instant revokedAt;
    private Instant createdAt;

    @TableField(exist = false)
    private UserEntity user;

    protected RefreshTokenEntity() {
    }

    public RefreshTokenEntity(UserEntity user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.userId = user.getId();
        this.tokenHash = tokenHash;
        this.status = TokenStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public UserEntity getUser() {
        return user;
    }

    public boolean isUsable(Instant now) {
        return status == TokenStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public void revoke() {
        this.status = TokenStatus.REVOKED;
        this.revokedAt = Instant.now();
    }
}
