package com.pptagent.domain.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.agent.AgentPlanEntity;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface AgentPlanRepository extends BaseMapper<AgentPlanEntity> {

    default Optional<AgentPlanEntity> findLatestByJobId(Long jobId) {
        return selectList(new QueryWrapper<AgentPlanEntity>()
                .eq("job_id", jobId)
                .orderByDesc("version_number")
                .last("limit 1")).stream().findFirst();
    }

    default List<AgentPlanEntity> findByJobId(Long jobId) {
        return selectList(new QueryWrapper<AgentPlanEntity>().eq("job_id", jobId).orderByAsc("version_number"));
    }

    default int updateOutlineIfRevision(Long planId, int expectedRevision, String outlineJson) {
        UpdateWrapper<AgentPlanEntity> update = new UpdateWrapper<AgentPlanEntity>()
                .eq("id", planId)
                .eq("outline_revision", expectedRevision)
                .set("outline_json", outlineJson)
                .set("outline_revision", expectedRevision + 1)
                .set("updated_at", Instant.now());
        return update(null, update);
    }
}
