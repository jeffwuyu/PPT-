package com.pptagent.domain.agent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("agent_generation_page_tasks")
public class AgentGenerationPageTaskEntity {
    @TableId
    private Long id;
    private Long generationJobId;
    private String pageId;
    private int pageOrder;
    private String status;
    private int attempts;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    protected AgentGenerationPageTaskEntity() { }

    public AgentGenerationPageTaskEntity(Long generationJobId, String pageId, int pageOrder) {
        this.generationJobId = generationJobId;
        this.pageId = pageId;
        this.pageOrder = pageOrder;
        this.status = "PENDING";
        this.attempts = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public Long getGenerationJobId() { return generationJobId; }
    public String getPageId() { return pageId; }
    public int getPageOrder() { return pageOrder; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getFailureReason() { return failureReason; }
}
