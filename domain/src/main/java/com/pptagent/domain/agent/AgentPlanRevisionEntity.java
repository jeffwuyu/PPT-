package com.pptagent.domain.agent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("agent_plan_revisions")
public class AgentPlanRevisionEntity {

    @TableId
    private Long id;
    private Long jobId;
    private Long previousPlanId;
    private Long newPlanId;
    private String reason;
    private Long checkpointId;
    private Instant createdAt;

    protected AgentPlanRevisionEntity() {
    }

    public AgentPlanRevisionEntity(Long jobId, Long previousPlanId, Long newPlanId, String reason, Long checkpointId) {
        this.jobId = jobId;
        this.previousPlanId = previousPlanId;
        this.newPlanId = newPlanId;
        this.reason = reason;
        this.checkpointId = checkpointId;
        this.createdAt = Instant.now();
    }
}
