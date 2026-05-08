package com.pptagent.collab.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptagent.infrastructure.config.RedisPlatformProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(prefix = "ppt.collaboration", name = "event-bus", havingValue = "redis")
/**
 * Redis Pub/Sub 版本的协同事件总线。
 *
 * <p>local event bus 只能覆盖单实例内的 WebSocket 连接；当部署多个
 * api-server 实例时，任意实例持久化的 update 都需要经由 Redis channel
 * 通知其他实例，再由各自实例广播给本地房间成员。</p>
 */
public class RedisCollaborationEventBus implements CollaborationEventBus, MessageListener {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisPlatformProperties redisProperties;
    private final List<Consumer<CollaborationBroadcastEvent>> consumers = new CopyOnWriteArrayList<>();

    public RedisCollaborationEventBus(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RedisPlatformProperties redisProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redisProperties = redisProperties;
    }

    @Override
    public void subscribe(Consumer<CollaborationBroadcastEvent> consumer) {
        consumers.add(consumer);
    }

    @Override
    public void publish(CollaborationBroadcastEvent event) {
        try {
            redisTemplate.convertAndSend(redisProperties.collaborationTopic(), objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish collaboration event", ex);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            CollaborationBroadcastEvent event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    CollaborationBroadcastEvent.class
            );
            consumers.forEach(consumer -> consumer.accept(event));
        } catch (Exception ignored) {
            // Redis channel 上的脏消息不应影响现有协同连接。
        }
    }
}
