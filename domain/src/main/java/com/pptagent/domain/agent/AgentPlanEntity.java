package com.pptagent.domain.agent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("agent_plans")
public class AgentPlanEntity {

    @TableId
    private Long id;
    private Long jobId;
    private String planMarkdown;
    private String planJson;
    private int versionNumber;
    private Instant createdAt;
    private Instant updatedAt;

    protected AgentPlanEntity() {
    }

    public AgentPlanEntity(Long jobId, String planMarkdown, String planJson, int versionNumber) {
        this.jobId = jobId;
        this.planMarkdown = planMarkdown;
        this.planJson = planJson;
        this.versionNumber = versionNumber;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getJobId() {
        return jobId;
    }

    public String getPlanMarkdown() {
        return planMarkdown;
    }

    public String getPlanJson() {
        return planJson;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void updatePlan(String planMarkdown, String planJson) {
        this.planMarkdown = planMarkdown;
        this.planJson = planJson;
        this.updatedAt = Instant.now();
    }
}
