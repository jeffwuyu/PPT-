package com.pptagent.collab.service;

import com.pptagent.domain.collaboration.CollaborationUpdateEntity;
import com.pptagent.domain.collaboration.repository.CollaborationUpdateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
/**
 * Yjs 增量持久化服务。
 *
 * <p>这里保存的是客户端传来的 base64 update 原文。自增主键 `id`
 * 同时作为对外的 updateId，用于客户端断线后按顺序补齐缺失增量。</p>
 */
public class CollaborationPersistenceService {

    private final CollaborationUpdateRepository updateRepository;

    public CollaborationPersistenceService(CollaborationUpdateRepository updateRepository) {
        this.updateRepository = updateRepository;
    }

    @Transactional
    public CollaborationUpdateEntity saveUpdate(Long presentationId, Long actorUserId, String clientId, String updateBase64) {
        CollaborationUpdateEntity update = new CollaborationUpdateEntity(presentationId, actorUserId, clientId, updateBase64);
        updateRepository.insert(update);
        return update;
    }

    @Transactional(readOnly = true)
    public List<CollaborationUpdateEntity> updatesAfter(Long presentationId, long updateId) {
        return updateRepository.findByPresentationIdAfterUpdateId(presentationId, updateId);
    }

    @Transactional(readOnly = true)
    public List<CollaborationUpdateEntity> allUpdates(Long presentationId) {
        return updateRepository.findByPresentationIdOrderByIdAsc(presentationId);
    }
}
