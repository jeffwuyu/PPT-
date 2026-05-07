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
@Table(name = "quota_consumptions")
public class QuotaConsumptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 60)
    private String quotaType;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 120)
    private String businessKey;

    @Column(nullable = false)
    private Instant createdAt;

    protected QuotaConsumptionEntity() {
    }

    public QuotaConsumptionEntity(UserEntity user, String quotaType, int amount, String businessKey) {
        this.user = user;
        this.quotaType = quotaType;
        this.amount = amount;
        this.businessKey = businessKey;
        this.createdAt = Instant.now();
    }
}
