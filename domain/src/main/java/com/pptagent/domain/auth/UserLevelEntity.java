package com.pptagent.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_levels")
public class UserLevelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false)
    private int rankOrder;

    @Column(nullable = false)
    private int maxProjects;

    @Column(nullable = false)
    private int agentDailyQuota;

    @Column(nullable = false)
    private Instant createdAt;

    protected UserLevelEntity() {
    }

    public UserLevelEntity(String code, String displayName, int rankOrder, int maxProjects, int agentDailyQuota) {
        this.code = code;
        this.displayName = displayName;
        this.rankOrder = rankOrder;
        this.maxProjects = maxProjects;
        this.agentDailyQuota = agentDailyQuota;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getRankOrder() {
        return rankOrder;
    }

    public int getMaxProjects() {
        return maxProjects;
    }

    public int getAgentDailyQuota() {
        return agentDailyQuota;
    }
}
