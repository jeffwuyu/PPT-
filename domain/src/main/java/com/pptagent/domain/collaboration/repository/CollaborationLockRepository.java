package com.pptagent.domain.collaboration.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.collaboration.CollaborationLockEntity;
import com.pptagent.domain.collaboration.CollaborationLockTargetType;

import java.time.Instant;
import java.util.Optional;

public interface CollaborationLockRepository extends BaseMapper<CollaborationLockEntity> {

    /**
     * 查找目标上的当前有效锁。
     *
     * <p>过期锁不需要后台任务立即清理；查询 active lock 时通过
     * `expires_at > now` 自然排除。</p>
     */
    default Optional<CollaborationLockEntity> findActiveLock(
            Long presentationId,
            CollaborationLockTargetType targetType,
            Long targetId,
            Instant now
    ) {
        return Optional.ofNullable(selectOne(new QueryWrapper<CollaborationLockEntity>()
                .eq("presentation_id", presentationId)
                .eq("target_type", targetType)
                .eq("target_id", targetId)
                .isNull("released_at")
                .gt("expires_at", now)
                .orderByDesc("id")
                .last("limit 1")));
    }

    default Optional<CollaborationLockEntity> findActiveByToken(String lockToken, Instant now) {
        return Optional.ofNullable(selectOne(new QueryWrapper<CollaborationLockEntity>()
                .eq("lock_token", lockToken)
                .isNull("released_at")
                .gt("expires_at", now)
                .last("limit 1")));
    }
}
