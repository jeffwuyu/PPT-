package com.pptagent.domain.agent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("agent_generation_jobs")
public class AgentGenerationJobEntity {
    @TableId
    private Long id;
    private Long agentJobId;
    private Long ownerUserId;
    private Long presentationId;
    private String idempotencyKey;
    private String status;
    private long revision;
    private int approvedOutlineRevision;
    private int totalPages;
    private int completedPages;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    protected AgentGenerationJobEntity() { }

    public AgentGenerationJobEntity(Long agentJobId, Long ownerUserId, Long presentationId,
                                    String idempotencyKey, int approvedOutlineRevision, int totalPages) {
        this.agentJobId = agentJobId;
        this.ownerUserId = ownerUserId;
        this.presentationId = presentationId;
        this.idempotencyKey = idempotencyKey;
        this.status = "QUEUED";
        this.revision = 1;
        this.approvedOutlineRevision = approvedOutlineRevision;
        this.totalPages = totalPages;
        this.completedPages = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public Long getAgentJobId() { return agentJobId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public Long getPresentationId() { return presentationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public long getRevision() { return revision; }
    public int getApprovedOutlineRevision() { return approvedOutlineRevision; }
    public int getTotalPages() { return totalPages; }
    public int getCompletedPages() { return completedPages; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
