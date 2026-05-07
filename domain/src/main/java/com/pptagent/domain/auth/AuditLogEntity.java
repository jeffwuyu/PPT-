package com.pptagent.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(nullable = false, length = 120)
    private String action;

    @Column(nullable = false, length = 80)
    private String resourceType;

    @Column(length = 120)
    private String resourceId;

    @Column(length = 1000)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLogEntity() {
    }

    public AuditLogEntity(UserEntity user, String action, String resourceType, String resourceId, String detail) {
        this.user = user;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.detail = detail;
        this.createdAt = Instant.now();
    }
}
