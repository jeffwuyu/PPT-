package com.pptagent.domain.presentation.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.presentation.SlideEntity;

import java.util.List;
import java.util.Optional;

public interface SlideRepository extends BaseMapper<SlideEntity> {

    // Phase 04：按标准页面顺序读取，用于 preview/version JSON。
    default List<SlideEntity> findByPresentationIdOrderByPageOrderAsc(Long presentationId) {
        return selectList(new QueryWrapper<SlideEntity>()
                .eq("presentation_id", presentationId)
                .eq("is_deleted", "0")
                .orderByAsc("page_order", "id"));
    }

    default long countByPresentationId(Long presentationId) {
        return selectCount(new QueryWrapper<SlideEntity>()
                .eq("presentation_id", presentationId)
                .eq("is_deleted", "0"));
    }

    default Optional<SlideEntity> findWithPresentationById(Long id) {
        return Optional.ofNullable(selectOne(new QueryWrapper<SlideEntity>()
                .eq("id", id)
                .eq("is_deleted", "0")));
    }
}
