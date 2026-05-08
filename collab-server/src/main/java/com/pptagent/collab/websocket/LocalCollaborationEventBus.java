package com.pptagent.collab.websocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(prefix = "ppt.collaboration", name = "event-bus", havingValue = "local", matchIfMissing = true)
public class LocalCollaborationEventBus implements CollaborationEventBus {

    private final List<Consumer<CollaborationBroadcastEvent>> consumers = new CopyOnWriteArrayList<>();

    @Override
    public void subscribe(Consumer<CollaborationBroadcastEvent> consumer) {
        consumers.add(consumer);
    }

    @Override
    public void publish(CollaborationBroadcastEvent event) {
        consumers.forEach(consumer -> consumer.accept(event));
    }
}
