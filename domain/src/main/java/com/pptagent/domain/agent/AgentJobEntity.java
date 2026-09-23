package com.pptagent.domain.agent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("agent_jobs")
public class AgentJobEntity {

    @TableId
    private Long id;
    private Long ownerUserId;
    private Long presentationId;
    private AgentJobStatus status;
    private Long activeGenerationJobId;
    private String promptText;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    protected AgentJobEntity() {
    }

    public AgentJobEntity(Long ownerUserId, Long presentationId, String promptText) {
        this.ownerUserId = ownerUserId;
        this.presentationId = presentationId;
        this.promptText = promptText;
        this.status = AgentJobStatus.UPLOADED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Long getPresentationId() {
        return presentationId;
    }

    public AgentJobStatus getStatus() {
        return status;
    }

    public Long getActiveGenerationJobId() {
        return activeGenerationJobId;
    }

    public String getPromptText() {
        return promptText;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void changeStatus(AgentJobStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
        if (status != AgentJobStatus.FAILED) {
            this.failureReason = null;
        }
    }

    public void fail(String reason) {
        this.status = AgentJobStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }
}
