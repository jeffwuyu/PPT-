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
    /** Versioned page-level outline edited by the user. */
    private String outlineJson;
    private int outlineRevision = 1;
    /** Immutable approved outline snapshot used by generation. */
    private String approvedOutlineJson;
    private Integer approvedOutlineRevision;
    private Instant createdAt;
    private Instant updatedAt;

    protected AgentPlanEntity() {
    }

    public AgentPlanEntity(Long jobId, String planMarkdown, String planJson, int versionNumber) {
        this.jobId = jobId;
        this.planMarkdown = planMarkdown;
        this.planJson = planJson;
        this.versionNumber = versionNumber;
        this.outlineJson = "{\"pages\":[]}";
        this.outlineRevision = 1;
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

    public String getOutlineJson() {
        return outlineJson;
    }

    public int getOutlineRevision() {
        return outlineRevision;
    }

    public String getApprovedOutlineJson() {
        return approvedOutlineJson;
    }

    public Integer getApprovedOutlineRevision() {
        return approvedOutlineRevision;
    }

    public void initializeOutline(String outlineJson) {
        this.outlineJson = outlineJson;
        this.outlineRevision = 1;
        this.updatedAt = Instant.now();
    }

    public void approveOutline() {
        this.approvedOutlineJson = this.outlineJson;
        this.approvedOutlineRevision = this.outlineRevision;
        this.updatedAt = Instant.now();
    }

    public void updatePlan(String planMarkdown, String planJson) {
        this.planMarkdown = planMarkdown;
        this.planJson = planJson;
        this.updatedAt = Instant.now();
    }
}
