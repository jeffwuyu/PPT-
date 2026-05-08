package com.pptagent.domain.collaboration.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.collaboration.CollaborationSnapshotEntity;

import java.util.Optional;

public interface CollaborationSnapshotRepository extends BaseMapper<CollaborationSnapshotEntity> {

    default Optional<CollaborationSnapshotEntity> findLatestByPresentationId(Long presentationId) {
        return Optional.ofNullable(selectOne(new QueryWrapper<CollaborationSnapshotEntity>()
                .eq("presentation_id", presentationId)
                .orderByDesc("snapshot_update_id")
                .orderByDesc("id")
                .last("limit 1")));
    }
}
