package com.pptagent.domain.agent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("agent_artifact_refs")
public class AgentArtifactRefEntity {

    @TableId
    private Long id;
    private Long jobId;
    private Long checkpointId;
    private AgentArtifactType artifactType;
    private Long artifactId;
    private String actionName;
    private boolean active;
    private String metadataJson;
    private Instant createdAt;
    private Instant updatedAt;

    protected AgentArtifactRefEntity() {
    }

    public AgentArtifactRefEntity(Long jobId, Long checkpointId, AgentArtifactType artifactType, Long artifactId, String actionName, String metadataJson) {
        this.jobId = jobId;
        this.checkpointId = checkpointId;
        this.artifactType = artifactType;
        this.artifactId = artifactId;
        this.actionName = actionName;
        this.active = true;
        this.metadataJson = metadataJson;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
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

    public AgentArtifactType getArtifactType() {
        return artifactType;
    }

    public Long getArtifactId() {
        return artifactId;
    }

    public boolean isActive() {
        return active;
    }

    public String getActionName() {
        return actionName;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void deactivate(String actionName) {
        this.active = false;
        this.actionName = actionName;
        this.updatedAt = Instant.now();
    }
}
