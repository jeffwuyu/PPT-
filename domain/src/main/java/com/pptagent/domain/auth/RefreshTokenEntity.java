package com.pptagent.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TokenStatus status;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected RefreshTokenEntity() {
    }

    public RefreshTokenEntity(UserEntity user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.status = TokenStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
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
