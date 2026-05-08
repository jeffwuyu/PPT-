package com.pptagent.collab.websocket;

import com.pptagent.collab.config.CollaborationProperties;
import com.pptagent.infrastructure.config.RedisPlatformProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
/**
 * 协同 WebSocket 配置。
 *
 * <p>endpoint 来自 `ppt.collaboration.websocket-endpoint`，当前为
 * `/ws/presentations`。实际注册为 `/*`，最后一段路径被握手拦截器
 * 解析成 presentationId。</p>
 */
public class CollaborationWebSocketConfig implements WebSocketConfigurer {

    private final CollaborationProperties properties;
    private final CollaborationWebSocketHandler handler;
    private final CollaborationHandshakeInterceptor handshakeInterceptor;

    public CollaborationWebSocketConfig(
            CollaborationProperties properties,
            CollaborationWebSocketHandler handler,
            CollaborationHandshakeInterceptor handshakeInterceptor
    ) {
        this.properties = properties;
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, properties.websocketEndpoint() + "/*")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }

    @Bean
    @ConditionalOnBean(RedisCollaborationEventBus.class)
    RedisMessageListenerContainer collaborationRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisCollaborationEventBus eventBus,
            RedisPlatformProperties redisProperties
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(eventBus, new ChannelTopic(redisProperties.collaborationTopic()));
        return container;
    }
}
