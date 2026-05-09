package com.pptagent.domain.presentation.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.presentation.PresentationExportEntity;

import java.util.List;

public interface PresentationExportRepository extends BaseMapper<PresentationExportEntity> {

    default List<PresentationExportEntity> findByPresentationIdOrderByCreatedAtDesc(Long presentationId) {
        return selectList(new QueryWrapper<PresentationExportEntity>()
                .eq("presentation_id", presentationId)
                .orderByDesc("created_at"));
    }
}
