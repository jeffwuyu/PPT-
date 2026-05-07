package com.pptagent.domain.presentation.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.presentation.SlideElementEntity;

import java.util.List;
import java.util.Optional;

public interface SlideElementRepository extends BaseMapper<SlideElementEntity> {

    // Phase 04：按标准元素顺序读取；这里不要按 z_index 排序。
    default List<SlideElementEntity> findBySlideIdOrderByPageOrderAsc(Long slideId) {
        return selectList(new QueryWrapper<SlideElementEntity>()
                .eq("slide_id", slideId)
                .orderByAsc("page_order", "id"));
    }

    default long countBySlideId(Long slideId) {
        return selectCount(new QueryWrapper<SlideElementEntity>().eq("slide_id", slideId));
    }

    default Optional<SlideElementEntity> findWithSlideAndPresentationById(Long id) {
        return Optional.ofNullable(selectById(id));
    }
}
