package com.pptagent.collab.websocket;

import com.pptagent.collab.security.CollaborationUser;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
/**
 * WebSocket 在线会话注册表。
 *
 * <p>注册表只保存当前 JVM 内的连接，用于 local 房间广播和 presence 组装。
 * 多实例部署时，跨实例 update 广播由 EventBus 负责；presence 跨实例聚合
 * 后续可以扩展到 Redis 状态缓存。</p>
 */
public class CollaborationSessionRegistry {

    private final ConcurrentMap<String, CollaborationSessionInfo> bySessionId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ConcurrentMap<String, CollaborationSessionInfo>> byPresentationId = new ConcurrentHashMap<>();

    public CollaborationSessionInfo register(WebSocketSession session, Long presentationId, CollaborationUser user) {
        CollaborationSessionInfo info = new CollaborationSessionInfo(session, presentationId, user);
        bySessionId.put(session.getId(), info);
        byPresentationId.computeIfAbsent(presentationId, ignored -> new ConcurrentHashMap<>())
                .put(session.getId(), info);
        return info;
    }

    public Optional<CollaborationSessionInfo> find(WebSocketSession session) {
        return Optional.ofNullable(bySessionId.get(session.getId()));
    }

    public void join(WebSocketSession session, String clientId) {
        find(session).ifPresent(info -> info.updateClientId(clientId));
    }

    public Optional<CollaborationSessionInfo> remove(WebSocketSession session) {
        CollaborationSessionInfo removed = bySessionId.remove(session.getId());
        if (removed != null) {
            /*
             * 两级索引必须一起清理。room 为空时移除 presentation 键，
             * 防止大量已关闭房间长期占用内存。
             */
            Map<String, CollaborationSessionInfo> room = byPresentationId.get(removed.presentationId());
            if (room != null) {
                room.remove(session.getId());
                if (room.isEmpty()) {
                    byPresentationId.remove(removed.presentationId(), room);
                }
            }
        }
        return Optional.ofNullable(removed);
    }

    public List<CollaborationSessionInfo> sessionsInRoom(Long presentationId) {
        Collection<CollaborationSessionInfo> values = byPresentationId
                .getOrDefault(presentationId, new ConcurrentHashMap<>())
                .values();
        return values.stream()
                .filter(info -> info.session().isOpen())
                .sorted(Comparator.comparing(CollaborationSessionInfo::clientId))
                .toList();
    }

    public void closeUserSessions(Long presentationId, Long userId) {
        for (CollaborationSessionInfo info : sessionsInRoom(presentationId)) {
            if (info.user().id().equals(userId)) {
                try {
                    info.session().close(CloseStatus.POLICY_VIOLATION);
                } catch (IOException ignored) {
                } finally {
                    remove(info.session());
                }
            }
        }
    }

    public List<Map<String, Object>> presence(Long presentationId) {
        List<Map<String, Object>> members = new ArrayList<>();
        for (CollaborationSessionInfo info : sessionsInRoom(presentationId)) {
            members.add(Map.of(
                    "userId", info.user().id(),
                    "email", info.user().email(),
                    "clientId", info.clientId(),
                    "connectedAt", info.connectedAt().toString()
            ));
        }
        return members;
    }
}
