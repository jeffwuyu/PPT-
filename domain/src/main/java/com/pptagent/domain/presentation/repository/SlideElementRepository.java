package com.pptagent.domain.presentation.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.pptagent.domain.presentation.SlideElementEntity;

import java.util.List;
import java.util.Optional;

public interface SlideElementRepository extends BaseMapper<SlideElementEntity> {

    default int updateRevisionIfCurrent(Long id, int expectedRevision, SlideElementEntity element) {
        return update(element, new UpdateWrapper<SlideElementEntity>()
                .eq("id", id)
                .eq("revision", expectedRevision)
                .eq("locked", false)
                .eq("is_deleted", "0"));
    }

    default int updateLockIfCurrent(Long id, int expectedRevision, boolean locked) {
        return update(null, new UpdateWrapper<SlideElementEntity>()
                .eq("id", id)
                .eq("revision", expectedRevision)
                .eq("is_deleted", "0")
                .set("locked", locked)
                .set("revision", expectedRevision + 1));
    }

    // Phase 04：按标准元素顺序读取；这里不要按 z_index 排序。
    default List<SlideElementEntity> findBySlideIdOrderByPageOrderAsc(Long slideId) {
        return selectList(new QueryWrapper<SlideElementEntity>()
                .eq("slide_id", slideId)
                .eq("is_deleted", "0")
                .orderByAsc("page_order", "id"));
    }

    default long countBySlideId(Long slideId) {
        return selectCount(new QueryWrapper<SlideElementEntity>()
                .eq("slide_id", slideId)
                .eq("is_deleted", "0"));
    }

    default Optional<SlideElementEntity> findWithSlideAndPresentationById(Long id) {
        return Optional.ofNullable(selectOne(new QueryWrapper<SlideElementEntity>()
                .eq("id", id)
                .eq("is_deleted", "0")));
    }
}
