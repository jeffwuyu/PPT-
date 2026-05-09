package com.pptagent.domain.agent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("agent_checkpoints")
public class AgentCheckpointEntity {

    @TableId
    private Long id;
    private Long jobId;
    private Long planId;
    private int checkpointOrder;
    private String title;
    private String objective;
    private AgentCheckpointStatus status;
    private int maxIterations;
    private int currentIterations;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    protected AgentCheckpointEntity() {
    }

    public AgentCheckpointEntity(Long jobId, Long planId, int checkpointOrder, String title, String objective, int maxIterations) {
        this.jobId = jobId;
        this.planId = planId;
        this.checkpointOrder = checkpointOrder;
        this.title = title;
        this.objective = objective;
        this.status = AgentCheckpointStatus.PENDING;
        this.maxIterations = maxIterations;
        this.currentIterations = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getJobId() {
        return jobId;
    }

    public Long getPlanId() {
        return planId;
    }

    public int getCheckpointOrder() {
        return checkpointOrder;
    }

    public String getTitle() {
        return title;
    }

    public String getObjective() {
        return objective;
    }

    public AgentCheckpointStatus getStatus() {
        return status;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public int getCurrentIterations() {
        return currentIterations;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void startNextIteration() {
        this.status = AgentCheckpointStatus.RUNNING;
        this.currentIterations = this.currentIterations + 1;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status = AgentCheckpointStatus.COMPLETED;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        this.status = AgentCheckpointStatus.CANCELLED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = AgentCheckpointStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }
}
