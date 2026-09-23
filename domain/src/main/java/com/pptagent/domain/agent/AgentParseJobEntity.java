package com.pptagent.domain.agent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("agent_parse_jobs")
public class AgentParseJobEntity {
    @TableId
    private Long id;
    private Long ownerUserId;
    private Long fileAssetId;
    private Long agentJobId;
    private String idempotencyKey;
    private String status;
    private String parserVersion;
    private String sourceHash;
    private int totalBlocks;
    private long revision;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    protected AgentParseJobEntity() { }

    public AgentParseJobEntity(Long ownerUserId, Long fileAssetId, Long agentJobId, String idempotencyKey) {
        this.ownerUserId = ownerUserId;
        this.fileAssetId = fileAssetId;
        this.agentJobId = agentJobId;
        this.idempotencyKey = idempotencyKey;
        this.status = "RUNNING";
        this.revision = 1;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public Long getFileAssetId() { return fileAssetId; }
    public Long getAgentJobId() { return agentJobId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public String getParserVersion() { return parserVersion; }
    public String getSourceHash() { return sourceHash; }
    public int getTotalBlocks() { return totalBlocks; }
    public long getRevision() { return revision; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void succeed(String sourceHash, String parserVersion, int totalBlocks) {
        this.status = "SUCCEEDED";
        this.sourceHash = sourceHash;
        this.parserVersion = parserVersion;
        this.totalBlocks = totalBlocks;
        this.failureReason = null;
        this.revision++;
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = "FAILED";
        this.failureReason = reason;
        this.revision++;
        this.updatedAt = Instant.now();
    }
}
