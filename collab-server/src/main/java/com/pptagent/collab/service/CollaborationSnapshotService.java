package com.pptagent.collab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.domain.collaboration.CollaborationSnapshotEntity;
import com.pptagent.domain.collaboration.CollaborationUpdateEntity;
import com.pptagent.domain.collaboration.repository.CollaborationSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
/**
 * 协同快照服务。
 *
 * <p>当前 snapshot 是“回放用检查点”，它把某个 presentation 已有的
 * update 按顺序包装成 JSON 保存，并不尝试在服务端压缩或合并 Yjs 文档。
 * 这样能先稳定断线恢复协议，后续如引入专用 Yjs 引擎再替换生成策略。</p>
 */
public class CollaborationSnapshotService {

    private final CollaborationSnapshotRepository snapshotRepository;
    private final CollaborationPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    public CollaborationSnapshotService(
            CollaborationSnapshotRepository snapshotRepository,
            CollaborationPersistenceService persistenceService,
            ObjectMapper objectMapper
    ) {
        this.snapshotRepository = snapshotRepository;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Optional<CollaborationSnapshotEntity> latestOrCreate(Long presentationId) {
        Optional<CollaborationSnapshotEntity> existing = snapshotRepository.findLatestByPresentationId(presentationId);
        if (existing.isPresent()) {
            return existing;
        }
        List<CollaborationUpdateEntity> updates = persistenceService.allUpdates(presentationId);
        if (updates.isEmpty()) {
            return Optional.empty();
        }
        /*
         * 懒生成策略：只有客户端请求同步且已有增量时才创建 snapshot。
         * 这样不会让每个 yjs-update 都承担快照写入成本。
         */
        Long latestUpdateId = updates.get(updates.size() - 1).getId();
        String snapshotJson = snapshotJson(updates);
        CollaborationSnapshotEntity snapshot = new CollaborationSnapshotEntity(
                presentationId,
                latestUpdateId,
                snapshotJson,
                updates.size()
        );
        snapshotRepository.insert(snapshot);
        return Optional.of(snapshot);
    }

    private String snapshotJson(List<CollaborationUpdateEntity> updates) {
        List<Map<String, Object>> compactUpdates = updates.stream()
                .map(update -> Map.<String, Object>of(
                        "updateId", update.getId(),
                        "actorUserId", update.getActorUserId(),
                        "clientId", update.getClientId(),
                        "type", "yjs-update",
                        "update", update.getPayloadBase64(),
                        "createdAt", update.getCreatedAt().toString()
                ))
                .toList();
        try {
            return objectMapper.writeValueAsString(Map.of("updates", compactUpdates));
        } catch (JsonProcessingException ex) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Failed to create collaboration snapshot");
        }
    }
}
