package com.pptagent.collab.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pptagent.common.error.ErrorCode;
import com.pptagent.common.error.PlatformException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Component
/**
 * 协同消息的 JSON 编解码与发送工具。
 *
 * <p>Handler 只关心业务分支，字段必填、类型读取、base64 校验和
 * WebSocket 发送细节集中在这里。这样协议错误能统一转换为
 * `PlatformException`，最终由 handler 返回 `error` 消息。</p>
 */
public class CollaborationMessageCodec {

    private final ObjectMapper objectMapper;

    public CollaborationMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Invalid collaboration JSON message");
        }
    }

    public String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Missing required field: " + field);
        }
        return value.asText();
    }

    public Long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Missing required field: " + field);
        }
        return value.asLong();
    }

    public long optionalLong(JsonNode node, String field, long defaultValue) {
        JsonNode value = node.get(field);
        return value != null && value.canConvertToLong() ? value.asLong() : defaultValue;
    }

    public String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    public void validateBase64(String payloadBase64) {
        try {
            Base64.getDecoder().decode(payloadBase64);
        } catch (IllegalArgumentException ex) {
            throw new PlatformException(ErrorCode.BAD_REQUEST, "Yjs update must be valid base64");
        }
    }

    public ObjectNode object(String type) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        return node;
    }

    public ObjectNode object(String type, Map<String, ?> values) {
        ObjectNode node = object(type);
        values.forEach((key, value) -> node.set(key, objectMapper.valueToTree(value)));
        return node;
    }

    public String stringify(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Failed to serialize collaboration message");
        }
    }

    public void send(WebSocketSession session, JsonNode node) throws IOException {
        /*
         * Tomcat 的 WebSocket remote endpoint 不允许同一 session 并发写。
         * presence、sync-state、update 广播可能从不同线程靠近发生，所以这里
         * 按 session 串行化发送，避免 TEXT_PARTIAL_WRITING 状态冲突。
         */
        synchronized (session) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(stringify(node)));
                } catch (IllegalStateException ignored) {
                    // 对端可能在 isOpen() 和 sendMessage() 之间关闭，视为陈旧连接。
                }
            }
        }
    }
}
