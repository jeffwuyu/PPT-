package com.pptagent.domain.agent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("agent_memories")
public class AgentMemoryEntity {

    @TableId
    private Long id;
    private Long jobId;
    private Long presentationId;
    private Long userId;
    private AgentMemoryScope memoryScope;
    private String memoryKind;
    private String contentText;
    private boolean compressed;
    private boolean archived;
    private Instant createdAt;
    private Instant updatedAt;

    protected AgentMemoryEntity() {
    }

    public AgentMemoryEntity(Long jobId, Long presentationId, Long userId, AgentMemoryScope memoryScope, String memoryKind, String contentText, boolean compressed) {
        this.jobId = jobId;
        this.presentationId = presentationId;
        this.userId = userId;
        this.memoryScope = memoryScope;
        this.memoryKind = memoryKind;
        this.contentText = contentText;
        this.compressed = compressed;
        this.archived = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public AgentMemoryScope getMemoryScope() {
        return memoryScope;
    }

    public String getMemoryKind() {
        return memoryKind;
    }

    public String getContentText() {
        return contentText;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public boolean isArchived() {
        return archived;
    }

    public void archive() {
        this.archived = true;
        this.updatedAt = Instant.now();
    }
}
