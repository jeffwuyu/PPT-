package com.pptagent.collab.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pptagent.collab.security.CollaborationAccessChecker;
import com.pptagent.collab.security.CollaborationTargetValidator;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import com.pptagent.collab.service.CollaborationPersistenceService;
import com.pptagent.collab.service.CollaborationSnapshotService;
import com.pptagent.collab.service.CollaborationSoftLockService;
import com.pptagent.domain.collaboration.CollaborationLockEntity;
import com.pptagent.domain.collaboration.CollaborationLockTargetType;
import com.pptagent.domain.collaboration.CollaborationSnapshotEntity;
import com.pptagent.domain.collaboration.CollaborationUpdateEntity;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
/**
 * 协同 WebSocket 的主入口。
 *
 * <p>这里故意只处理“协同传输层”的职责：消息分发、房间广播、
 * update 持久化、断线同步和软锁校验。Yjs update 本身被视为
 * opaque base64 payload，服务端不解析 CRDT 语义，避免 Java 端
 * 与前端 Yjs 实现产生双写状态机。</p>
 */
public class CollaborationWebSocketHandler extends TextWebSocketHandler {

    private final CollaborationSessionRegistry sessionRegistry;
    private final CollaborationMessageCodec codec;
    private final CollaborationAccessChecker accessChecker;
    private final CollaborationPersistenceService persistenceService;
    private final CollaborationSnapshotService snapshotService;
    private final CollaborationSoftLockService softLockService;
    private final CollaborationTargetValidator targetValidator;
    private final CollaborationEventBus eventBus;

    public CollaborationWebSocketHandler(
            CollaborationSessionRegistry sessionRegistry,
            CollaborationMessageCodec codec,
            CollaborationAccessChecker accessChecker,
            CollaborationPersistenceService persistenceService,
            CollaborationSnapshotService snapshotService,
            CollaborationSoftLockService softLockService,
            CollaborationTargetValidator targetValidator,
            CollaborationEventBus eventBus
    ) {
        this.sessionRegistry = sessionRegistry;
        this.codec = codec;
        this.accessChecker = accessChecker;
        this.persistenceService = persistenceService;
        this.snapshotService = snapshotService;
        this.softLockService = softLockService;
        this.targetValidator = targetValidator;
        this.eventBus = eventBus;
    }

    @PostConstruct
    void subscribeToBroadcasts() {
        /*
         * EventBus 把“本实例 WebSocket 房间广播”和“未来多实例 Redis 广播”
         * 隔离开。local 模式下这里就是内存回调；redis 模式下会收到其他实例
         * 发布的同一事件。
         */
        eventBus.subscribe(this::broadcastUpdateEvent);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long presentationId = (Long) session.getAttributes().get(CollaborationHandshakeInterceptor.ATTR_PRESENTATION_ID);
        var user = (com.pptagent.collab.security.CollaborationUser) session.getAttributes()
                .get(CollaborationHandshakeInterceptor.ATTR_USER);
        sessionRegistry.register(session, presentationId, user);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        try {
            JsonNode node = codec.parse(message.getPayload());
            String type = codec.requiredText(node, "type");
            switch (type) {
                case CollaborationMessageType.JOIN -> handleJoin(session, node);
                case CollaborationMessageType.CURSOR -> handleCursor(session, node);
                case CollaborationMessageType.YJS_UPDATE -> handleYjsUpdate(session, node);
                case CollaborationMessageType.SYNC_REQUEST -> handleSyncRequest(session, node);
                case CollaborationMessageType.LOCK_ACQUIRE -> handleLockAcquire(session, node);
                case CollaborationMessageType.LOCK_RELEASE -> handleLockRelease(session, node);
                default -> throw new PlatformException(ErrorCode.BAD_REQUEST, "Unsupported collaboration message: " + type);
            }
        } catch (PlatformException ex) {
            sendError(session, ex.getErrorCode().name(), ex.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws IOException {
        Optional<CollaborationSessionInfo> removed = sessionRegistry.remove(session);
        if (removed.isPresent()) {
            broadcastPresence(removed.get().presentationId());
        }
    }

    private void handleJoin(WebSocketSession session, JsonNode node) throws IOException {
        CollaborationSessionInfo info = requireSession(session);
        String clientId = codec.requiredText(node, "clientId");
        sessionRegistry.join(session, clientId);
        broadcastPresence(info.presentationId());
        /*
         * join 同时承担轻量 sync-request 的职责：客户端可携带 lastUpdateId，
         * 服务端会返回 snapshot 和缺失 updates，便于断线重连后快速追平。
         */
        sendSyncState(session, info.presentationId(), codec.optionalLong(node, "lastUpdateId", 0L));
    }

    private void handleCursor(WebSocketSession session, JsonNode node) throws IOException {
        CollaborationSessionInfo info = requireSession(session);
        String clientId = codec.requiredText(node, "clientId");
        Long slideId = codec.requiredLong(node, "slideId");
        ObjectNode outbound = codec.object(CollaborationMessageType.CURSOR, Map.of(
                "actorUserId", info.user().id(),
                "clientId", clientId,
                "slideId", slideId
        ));
        putOptional(outbound, "elementId", optionalLongBoxed(node, "elementId"));
        putOptional(outbound, "x", optionalDoubleBoxed(node, "x"));
        putOptional(outbound, "y", optionalDoubleBoxed(node, "y"));
        // 光标是瞬时协同状态，只实时转发，不写入数据库。
        sendToRoomExcept(info.presentationId(), session.getId(), outbound);
    }

    private void handleYjsUpdate(WebSocketSession session, JsonNode node) throws IOException {
        CollaborationSessionInfo info = requireSession(session);
        requireEdit(info);
        String clientId = codec.requiredText(node, "clientId");
        String update = codec.requiredText(node, "update");
        codec.validateBase64(update);

        CollaborationLockTargetType targetType = optionalTargetType(node);
        Long targetId = optionalLongBoxed(node, "targetId");
        String lockToken = codec.optionalText(node, "lockToken");
        validateOptionalTarget(info, targetType, targetId);
        /*
         * CRDT 能解决普通并发编辑，但删除 Slide、冻结导出等高风险动作
         * 仍需要软锁保护。未指定 target 时表示普通增量，直接允许。
         */
        if (!softLockService.validatesLockToken(info.presentationId(), targetType, targetId, lockToken, info.user().id(), clientId)) {
            throw new PlatformException(ErrorCode.CONFLICT, "Collaboration soft lock is held by another client");
        }

        CollaborationUpdateEntity saved = persistenceService.saveUpdate(info.presentationId(), info.user().id(), clientId, update);
        codec.send(session, codec.object(CollaborationMessageType.YJS_ACK, Map.of(
                "updateId", saved.getId(),
                "clientId", clientId
        )));
        eventBus.publish(new CollaborationBroadcastEvent(
                info.presentationId(),
                session.getId(),
                info.user().id(),
                clientId,
                saved.getId(),
                update
        ));
    }

    private void handleSyncRequest(WebSocketSession session, JsonNode node) throws IOException {
        CollaborationSessionInfo info = requireSession(session);
        sendSyncState(session, info.presentationId(), codec.optionalLong(node, "lastUpdateId", 0L));
    }

    private void handleLockAcquire(WebSocketSession session, JsonNode node) throws IOException {
        CollaborationSessionInfo info = requireSession(session);
        requireEdit(info);
        String clientId = codec.requiredText(node, "clientId");
        CollaborationLockTargetType targetType = requiredTargetType(node);
        Long targetId = codec.requiredLong(node, "targetId");
        String operation = codec.requiredText(node, "operation");
        String lockToken = codec.requiredText(node, "lockToken");
        targetValidator.validateTarget(info.user().id(), info.presentationId(), targetType, targetId);

        CollaborationSoftLockService.LockResult result = softLockService.acquire(
                info.presentationId(),
                targetType,
                targetId,
                info.user().id(),
                clientId,
                lockToken,
                operation
        );
        CollaborationLockEntity lock = result.lock();
        codec.send(session, codec.object(result.granted()
                ? CollaborationMessageType.LOCK_GRANTED
                : CollaborationMessageType.LOCK_DENIED, lockPayload(lock)));
    }

    private void handleLockRelease(WebSocketSession session, JsonNode node) throws IOException {
        CollaborationSessionInfo info = requireSession(session);
        requireEdit(info);
        String clientId = codec.requiredText(node, "clientId");
        String lockToken = codec.requiredText(node, "lockToken");
        CollaborationLockEntity lock = softLockService.release(lockToken, info.user().id(), clientId);
        ObjectNode message = codec.object(CollaborationMessageType.LOCK_RELEASED, lockPayload(lock));
        sendToRoom(info.presentationId(), message);
    }

    private void sendSyncState(WebSocketSession session, Long presentationId, long lastUpdateId) throws IOException {
        /*
         * Snapshot 当前是“可回放检查点”，不是 Yjs merge 后的压缩文档。
         * 返回 snapshot + lastUpdateId 之后的增量，客户端仍按 Yjs 自己的
         * 合并规则恢复最终状态。
         */
        Optional<CollaborationSnapshotEntity> snapshot = snapshotService.latestOrCreate(presentationId);
        List<Map<String, Object>> updates = persistenceService.updatesAfter(presentationId, lastUpdateId).stream()
                .map(update -> Map.<String, Object>of(
                        "updateId", update.getId(),
                        "actorUserId", update.getActorUserId(),
                        "clientId", update.getClientId(),
                        "type", CollaborationMessageType.YJS_UPDATE,
                        "update", update.getPayloadBase64(),
                        "createdAt", update.getCreatedAt().toString()
                ))
                .toList();
        Map<String, Object> snapshotPayload = snapshot
                .<Map<String, Object>>map(value -> Map.of(
                        "snapshotUpdateId", value.getSnapshotUpdateId(),
                        "snapshot", value.getSnapshotJson(),
                        "updateCount", value.getUpdateCount(),
                        "createdAt", value.getCreatedAt().toString()
                ))
                .orElse(Map.of());
        codec.send(session, codec.object(CollaborationMessageType.SYNC_STATE, Map.of(
                "presentationId", presentationId,
                "lastUpdateId", lastUpdateId,
                "snapshot", snapshotPayload,
                "updates", updates
        )));
    }

    private void broadcastUpdateEvent(CollaborationBroadcastEvent event) {
        ObjectNode outbound = codec.object(CollaborationMessageType.YJS_UPDATE, Map.of(
                "updateId", event.updateId(),
                "actorUserId", event.actorUserId(),
                "clientId", event.clientId(),
                "update", event.payloadBase64()
        ));
        sendToRoomExcept(event.presentationId(), event.originSessionId(), outbound);
    }

    private void broadcastPresence(Long presentationId) throws IOException {
        sendToRoom(presentationId, codec.object(CollaborationMessageType.PRESENCE, Map.of(
                "presentationId", presentationId,
                "members", sessionRegistry.presence(presentationId)
        )));
    }

    private void sendToRoom(Long presentationId, JsonNode node) {
        /*
         * 房间发送对单个 session 已在 codec 内串行化。这里吞掉 IOException，
         * 因为断开的连接会在 close 回调中被清理，不应影响其他成员收消息。
         */
        for (CollaborationSessionInfo info : sessionRegistry.sessionsInRoom(presentationId)) {
            try {
                codec.send(info.session(), node);
            } catch (IOException ignored) {
            }
        }
    }

    private void sendToRoomExcept(Long presentationId, String excludedSessionId, JsonNode node) {
        for (CollaborationSessionInfo info : sessionRegistry.sessionsInRoom(presentationId)) {
            if (!info.session().getId().equals(excludedSessionId)) {
                try {
                    codec.send(info.session(), node);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private CollaborationSessionInfo requireSession(WebSocketSession session) {
        return sessionRegistry.find(session)
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHORIZED, "Collaboration session not registered"));
    }

    private void requireEdit(CollaborationSessionInfo info) {
        if (!accessChecker.canEditPresentation(info.user().id(), info.presentationId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "No permission to edit presentation");
        }
    }

    private void validateOptionalTarget(CollaborationSessionInfo info, CollaborationLockTargetType targetType, Long targetId) {
        if (targetType == null && targetId == null) {
            return;
        }
        if (targetType == null || targetId == null) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Both targetType and targetId are required");
        }
        targetValidator.validateTarget(info.user().id(), info.presentationId(), targetType, targetId);
    }

    private void sendError(WebSocketSession session, String code, String message) throws IOException {
        codec.send(session, codec.object(CollaborationMessageType.ERROR, Map.of(
                "code", code,
                "message", message
        )));
    }

    private CollaborationLockTargetType requiredTargetType(JsonNode node) {
        String raw = codec.requiredText(node, "targetType");
        try {
            return CollaborationLockTargetType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Invalid lock targetType");
        }
    }

    private CollaborationLockTargetType optionalTargetType(JsonNode node) {
        String raw = codec.optionalText(node, "targetType");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return CollaborationLockTargetType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Invalid lock targetType");
        }
    }

    private Map<String, Object> lockPayload(CollaborationLockEntity lock) {
        return Map.of(
                "lockId", lock.getId(),
                "targetType", lock.getTargetType(),
                "targetId", lock.getTargetId(),
                "ownerUserId", lock.getOwnerUserId(),
                "clientId", lock.getClientId(),
                "lockToken", lock.getLockToken(),
                "operation", lock.getOperation(),
                "expiresAt", lock.getExpiresAt().toString()
        );
    }

    private Long optionalLongBoxed(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.canConvertToLong() ? value.asLong() : null;
    }

    private Double optionalDoubleBoxed(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    private void putOptional(ObjectNode node, String field, Long value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    private void putOptional(ObjectNode node, String field, Double value) {
        if (value != null) {
            node.put(field, value);
        }
    }
}
