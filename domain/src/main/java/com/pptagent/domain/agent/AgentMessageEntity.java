package com.pptagent.domain.agent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("agent_messages")
public class AgentMessageEntity {

    @TableId
    private Long id;
    private Long jobId;
    private Long checkpointId;
    private String roleName;
    private String contentText;
    private String metadataJson;
    private Instant createdAt;

    protected AgentMessageEntity() {
    }

    public AgentMessageEntity(Long jobId, Long checkpointId, String roleName, String contentText, String metadataJson) {
        this.jobId = jobId;
        this.checkpointId = checkpointId;
        this.roleName = roleName;
        this.contentText = contentText;
        this.metadataJson = metadataJson;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getJobId() {
        return jobId;
    }

    public Long getCheckpointId() {
        return checkpointId;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getContentText() {
        return contentText;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
