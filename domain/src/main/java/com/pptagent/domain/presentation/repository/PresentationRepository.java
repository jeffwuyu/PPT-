package com.pptagent.domain.presentation.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.presentation.PresentationEntity;
import com.pptagent.domain.presentation.PresentationStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PresentationRepository extends BaseMapper<PresentationEntity> {

    default Optional<PresentationEntity> findWithOwnerByIdAndStatus(Long id, PresentationStatus status) {
        return Optional.ofNullable(selectOne(new QueryWrapper<PresentationEntity>()
                .eq("id", id)
                .eq("status", status)
                .eq("is_deleted", "0")));
    }

    default Optional<PresentationEntity> findNotDeletedById(Long id) {
        return Optional.ofNullable(selectOne(new QueryWrapper<PresentationEntity>()
                .eq("id", id)
                .eq("is_deleted", "0")));
    }

    default List<PresentationEntity> findNotDeletedByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(new QueryWrapper<PresentationEntity>()
                .in("id", ids)
                .eq("is_deleted", "0")
                .orderByDesc("updated_at", "id"));
    }
}
