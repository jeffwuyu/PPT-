package com.pptagent.collab.websocket;

import com.pptagent.collab.security.CollaborationUser;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public class CollaborationSessionInfo {

    private final WebSocketSession session;
    private final Long presentationId;
    private final CollaborationUser user;
    private final Instant connectedAt;
    private final AtomicReference<String> clientId;

    public CollaborationSessionInfo(WebSocketSession session, Long presentationId, CollaborationUser user) {
        this.session = session;
        this.presentationId = presentationId;
        this.user = user;
        this.connectedAt = Instant.now();
        this.clientId = new AtomicReference<>(session.getId());
    }

    public WebSocketSession session() {
        return session;
    }

    public Long presentationId() {
        return presentationId;
    }

    public CollaborationUser user() {
        return user;
    }

    public Instant connectedAt() {
        return connectedAt;
    }

    public String clientId() {
        return clientId.get();
    }

    public void updateClientId(String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            this.clientId.set(clientId);
        }
    }
}
