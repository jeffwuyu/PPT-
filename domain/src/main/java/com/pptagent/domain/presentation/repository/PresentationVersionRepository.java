package com.pptagent.domain.presentation.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.presentation.PresentationVersionEntity;

import java.util.List;
import java.util.Optional;

public interface PresentationVersionRepository extends BaseMapper<PresentationVersionEntity> {

    // Phase 04：版本历史通过 Presentation 内单调递增的 versionNumber 定位。
    default List<PresentationVersionEntity> findByPresentationIdOrderByVersionNumberDesc(Long presentationId) {
        return selectList(new QueryWrapper<PresentationVersionEntity>()
                .eq("presentation_id", presentationId)
                .orderByDesc("version_number"));
    }

    default Optional<PresentationVersionEntity> findByPresentationIdAndVersionNumber(Long presentationId, int versionNumber) {
        return Optional.ofNullable(selectOne(new QueryWrapper<PresentationVersionEntity>()
                .eq("presentation_id", presentationId)
                .eq("version_number", versionNumber)));
    }
}
