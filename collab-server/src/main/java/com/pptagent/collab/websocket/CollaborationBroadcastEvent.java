package com.pptagent.collab.websocket;

public record CollaborationBroadcastEvent(
        Long presentationId,
        String originSessionId,
        Long actorUserId,
        String clientId,
        Long updateId,
        String payloadBase64
) {
}
