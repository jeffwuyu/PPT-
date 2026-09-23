package com.pptagent.domain.agent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("agent_sources")
public class AgentSourceEntity {

    @TableId
    private Long id;
    private Long jobId;
    private AgentSourceType sourceType;
    private Long fileAssetId;
    private String contentText;
    private String metadataJson;
    private Instant createdAt;

    protected AgentSourceEntity() {
    }

    public AgentSourceEntity(Long jobId, AgentSourceType sourceType, Long fileAssetId, String contentText, String metadataJson) {
        this.jobId = jobId;
        this.sourceType = sourceType;
        this.fileAssetId = fileAssetId;
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

    public AgentSourceType getSourceType() {
        return sourceType;
    }

    public Long getFileAssetId() {
        return fileAssetId;
    }

    public String getContentText() {
        return contentText;
    }

    public String getMetadataJson() {
        return metadataJson;
    }
}
