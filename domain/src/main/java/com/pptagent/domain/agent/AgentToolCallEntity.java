package com.pptagent.domain.agent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("agent_tool_calls")
public class AgentToolCallEntity {

    @TableId
    private Long id;
    private Long jobId;
    private Long checkpointId;
    private String toolName;
    private String requestJson;
    private String responseJson;
    private AgentToolCallStatus status;
    private long durationMs;
    private String errorMessage;
    private Instant createdAt;

    protected AgentToolCallEntity() {
    }

    public AgentToolCallEntity(Long jobId, Long checkpointId, String toolName, String requestJson) {
        this.jobId = jobId;
        this.checkpointId = checkpointId;
        this.toolName = toolName;
        this.requestJson = requestJson;
        this.status = AgentToolCallStatus.FAILED;
        this.durationMs = 0;
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

    public String getToolName() {
        return toolName;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public AgentToolCallStatus getStatus() {
        return status;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void finish(AgentToolCallStatus status, String responseJson, long durationMs, String errorMessage) {
        this.status = status;
        this.responseJson = responseJson;
        this.durationMs = durationMs;
        this.errorMessage = errorMessage;
    }
}
