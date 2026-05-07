package com.pptagent.domain.presentation.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.presentation.PresentationEntity;
import com.pptagent.domain.presentation.PresentationStatus;

import java.util.Optional;

public interface PresentationRepository extends BaseMapper<PresentationEntity> {

    default Optional<PresentationEntity> findWithOwnerByIdAndStatus(Long id, PresentationStatus status) {
        return Optional.ofNullable(selectOne(new QueryWrapper<PresentationEntity>()
                .eq("id", id)
                .eq("status", status)));
    }
}
