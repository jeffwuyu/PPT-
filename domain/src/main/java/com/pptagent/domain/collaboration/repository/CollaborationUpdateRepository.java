package com.pptagent.domain.collaboration.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pptagent.domain.collaboration.CollaborationUpdateEntity;

import java.util.List;
import java.util.Optional;

public interface CollaborationUpdateRepository extends BaseMapper<CollaborationUpdateEntity> {

    /**
     * 读取指定 updateId 之后的增量。
     *
     * <p>断线重连时客户端提交自己最后处理过的 updateId，
     * 服务端按 id 升序返回缺失增量，确保回放顺序稳定。</p>
     */
    default List<CollaborationUpdateEntity> findByPresentationIdAfterUpdateId(Long presentationId, long updateId) {
        return selectList(new QueryWrapper<CollaborationUpdateEntity>()
                .eq("presentation_id", presentationId)
                .gt("id", updateId)
                .orderByAsc("id"));
    }

    default List<CollaborationUpdateEntity> findByPresentationIdOrderByIdAsc(Long presentationId) {
        return selectList(new QueryWrapper<CollaborationUpdateEntity>()
                .eq("presentation_id", presentationId)
                .orderByAsc("id"));
    }

    default Optional<CollaborationUpdateEntity> findLatestByPresentationId(Long presentationId) {
        return Optional.ofNullable(selectOne(new QueryWrapper<CollaborationUpdateEntity>()
                .eq("presentation_id", presentationId)
                .orderByDesc("id")
                .last("limit 1")));
    }
}
