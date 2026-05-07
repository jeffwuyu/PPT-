package com.pptagent.domain.auth;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("user_levels")
public class UserLevelEntity {

    @TableId
    private Long id;
    private String code;
    private String displayName;
    private int rankOrder;
    private int maxProjects;
    private int agentDailyQuota;
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
