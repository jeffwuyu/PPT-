package com.pptagent.collab.websocket;

import java.util.function.Consumer;

public interface CollaborationEventBus {

    void subscribe(Consumer<CollaborationBroadcastEvent> consumer);

    void publish(CollaborationBroadcastEvent event);
}
