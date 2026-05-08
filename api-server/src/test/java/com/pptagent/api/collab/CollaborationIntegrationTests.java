package com.pptagent.api.collab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ppt.jwt.access-token-ttl=30s")
@AutoConfigureMockMvc
/**
 * Phase 05 的真实 WebSocket 集成测试。
 *
 * <p>测试使用随机端口启动 Tomcat，而不是只测 handler 单元逻辑。
 * 这样可以覆盖握手、Spring Security 放行、HandshakeInterceptor 鉴权、
 * WebSocket 消息收发和数据库持久化完整链路。</p>
 */
class CollaborationIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void websocketHandshakeRejectsMissingInvalidAndUnauthorizedTokens() throws Exception {
        String ownerToken = registerAndToken("collab-owner");
        String otherToken = registerAndToken("collab-other");
        long presentationId = createPresentation(ownerToken, "Collab Auth").at("/data/id").asLong();

        assertThatThrownBy(() -> connect(presentationId, null, null).close())
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connect(presentationId, "not-a-token", null).close())
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connect(presentationId, otherToken, null).close())
                .isInstanceOf(Exception.class);
    }

    @Test
    void sharedViewersCanJoinButOnlyEditorsCanSendUpdates() throws Exception {
        RegisteredUser owner = registerUser("collab-share-owner");
        RegisteredUser member = registerUser("collab-share-member");
        long presentationId = createPresentation(owner.token(), "Collab Share").at("/data/id").asLong();

        sharePresentationMember(owner.token(), presentationId, member.email(), false);

        TestSocket memberSocket = connect(presentationId, member.token(), null);
        memberSocket.send("""
                {"type":"join","clientId":"viewer-client","lastUpdateId":0}
                """);
        memberSocket.awaitType("sync-state");

        memberSocket.send("""
                {"type":"yjs-update","clientId":"viewer-client","update":"AQIDBA=="}
                """);
        JsonNode viewerError = memberSocket.awaitType("error");
        assertThat(viewerError.at("/code").asText()).isEqualTo("FORBIDDEN");

        sharePresentationMember(owner.token(), presentationId, member.email(), true);
        memberSocket.send("""
                {"type":"yjs-update","clientId":"viewer-client","update":"BQYHCA=="}
                """);
        assertThat(memberSocket.awaitType("yjs-ack").at("/clientId").asText()).isEqualTo("viewer-client");

        memberSocket.close();
    }

    @Test
    void clientsExchangePresenceCursorUpdatesAndReconnectSyncState() throws Exception {
        String token = registerAndToken("collab-flow");
        long presentationId = createPresentation(token, "Collab Flow").at("/data/id").asLong();

        TestSocket clientA = connect(presentationId, token, null);
        TestSocket clientB = connect(presentationId, token, null);
        clientA.send("""
                {"type":"join","clientId":"client-a","lastUpdateId":0}
                """);
        clientB.send("""
                {"type":"join","clientId":"client-b","lastUpdateId":0}
                """);

        JsonNode presence = clientA.awaitType("presence", node -> node.at("/members").size() == 2);
        assertThat(presence.at("/members").size()).isEqualTo(2);

        clientA.send("""
                {"type":"cursor","clientId":"client-a","slideId":101,"x":12.5,"y":33.0}
                """);
        JsonNode cursor = clientB.awaitType("cursor");
        assertThat(cursor.at("/clientId").asText()).isEqualTo("client-a");
        assertThat(cursor.at("/slideId").asLong()).isEqualTo(101L);

        clientA.send("""
                {"type":"yjs-update","clientId":"client-a","update":"AQIDBA=="}
                """);
        /*
         * 服务端不解析 AQIDBA== 的 Yjs 语义，只验证 base64 合法、
         * 分配 updateId、落库并广播给同房间其他客户端。
         */
        JsonNode firstAck = clientA.awaitType("yjs-ack");
        long firstUpdateId = firstAck.at("/updateId").asLong();
        JsonNode broadcast = clientB.awaitType("yjs-update");
        assertThat(broadcast.at("/updateId").asLong()).isEqualTo(firstUpdateId);
        assertThat(broadcast.at("/update").asText()).isEqualTo("AQIDBA==");

        clientB.close();
        clientA.send("""
                {"type":"yjs-update","clientId":"client-a","update":"BQYHCA=="}
                """);
        long secondUpdateId = clientA.awaitType("yjs-ack").at("/updateId").asLong();
        assertThat(secondUpdateId).isGreaterThan(firstUpdateId);

        TestSocket reconnectingClient = connect(presentationId, token, null);
        reconnectingClient.send("""
                {"type":"join","clientId":"client-b","lastUpdateId":%d}
                """.formatted(firstUpdateId));
        /*
         * 重连客户端声明自己已处理 firstUpdateId，服务端应只返回后续缺失增量。
         * snapshot 字段用于未来更快恢复完整状态。
         */
        JsonNode syncState = reconnectingClient.awaitType("sync-state", node -> node.at("/updates").size() == 1);
        assertThat(syncState.at("/snapshot/snapshotUpdateId").asLong()).isGreaterThanOrEqualTo(secondUpdateId);
        assertThat(syncState.at("/updates/0/updateId").asLong()).isEqualTo(secondUpdateId);
        assertThat(syncState.at("/updates/0/update").asText()).isEqualTo("BQYHCA==");

        clientA.close();
        reconnectingClient.close();
    }

    @Test
    void softLocksPreventConcurrentHighRiskOperationsUntilReleased() throws Exception {
        String token = registerAndToken("collab-lock");
        long presentationId = createPresentation(token, "Collab Locks").at("/data/id").asLong();

        TestSocket clientA = connect(presentationId, token, null);
        TestSocket clientB = connect(presentationId, token, null);
        clientA.send("""
                {"type":"join","clientId":"client-a"}
                """);
        clientB.send("""
                {"type":"join","clientId":"client-b"}
                """);
        clientA.awaitType("presence");
        clientB.awaitType("presence");

        clientA.send("""
                {"type":"lock-acquire","clientId":"client-a","targetType":"SLIDE","targetId":999999,"operation":"delete-slide","lockToken":"missing-target"}
                """);
        assertThat(clientA.awaitType("error").at("/code").asText()).isEqualTo("NOT_FOUND");

        clientA.send("""
                {"type":"lock-acquire","clientId":"client-a","targetType":"PRESENTATION","targetId":%d,"operation":"delete-slide","lockToken":"lock-a"}
                """.formatted(presentationId));
        assertThat(clientA.awaitType("lock-granted").at("/lockToken").asText()).isEqualTo("lock-a");

        clientB.send("""
                {"type":"lock-acquire","clientId":"client-b","targetType":"PRESENTATION","targetId":%d,"operation":"delete-slide","lockToken":"lock-b"}
                """.formatted(presentationId));
        assertThat(clientB.awaitType("lock-denied").at("/lockToken").asText()).isEqualTo("lock-a");

        clientA.send("""
                {"type":"lock-release","clientId":"client-a","lockToken":"lock-a"}
                """);
        clientA.awaitType("lock-released");

        clientB.send("""
                {"type":"lock-acquire","clientId":"client-b","targetType":"PRESENTATION","targetId":%d,"operation":"delete-slide","lockToken":"lock-b"}
                """.formatted(presentationId));
        assertThat(clientB.awaitType("lock-granted").at("/lockToken").asText()).isEqualTo("lock-b");

        clientA.close();
        clientB.close();
    }

    private TestSocket connect(long presentationId, String token, WebSocketHttpHeaders headers) throws Exception {
        TestSocket handler = new TestSocket(objectMapper);
        WebSocketHttpHeaders actualHeaders = headers == null ? new WebSocketHttpHeaders() : headers;
        URI uri = URI.create("ws://localhost:%d/ws/presentations/%d%s".formatted(
                port,
                presentationId,
                token == null ? "" : "?access_token=" + token
        ));
        handler.session = new StandardWebSocketClient()
                .execute(handler, actualHeaders, uri)
                .get(5, TimeUnit.SECONDS);
        return handler;
    }

    private JsonNode createPresentation(String token, String title) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/presentations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s"}
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private void sharePresentationMember(String token, long presentationId, String email, boolean canEdit) throws Exception {
        mockMvc.perform(post("/api/presentations/" + presentationId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","canEdit":%s}
                                """.formatted(email, canEdit)))
                .andExpect(status().isOk());
    }

    private String registerAndToken(String prefix) throws Exception {
        return registerUser(prefix).token();
    }

    private RegisteredUser registerUser(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = "%s-%s@example.test".formatted(prefix, suffix);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s-%s@example.test","username":"%s%s","password":"StrongPass123"}
                                """.formatted(prefix, suffix, prefix.replace("-", ""), suffix.substring(0, 8))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new RegisteredUser(email, objectMapper.readTree(response).at("/data/accessToken").asText());
    }

    private record RegisteredUser(String email, String token) {
    }

    private static class TestSocket extends TextWebSocketHandler {

        private final ObjectMapper objectMapper;
        private final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        private WebSocketSession session;

        private TestSocket(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            messages.add(objectMapper.readTree(message.getPayload()));
        }

        JsonNode awaitType(String type) throws InterruptedException {
            return awaitType(type, ignored -> true);
        }

        JsonNode awaitType(String type, Predicate<JsonNode> predicate) throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < deadline) {
                JsonNode node = messages.poll(200, TimeUnit.MILLISECONDS);
                if (node != null && type.equals(node.at("/type").asText()) && predicate.test(node)) {
                    return node;
                }
            }
            throw new AssertionError("Timed out waiting for WebSocket message type: " + type);
        }

        void send(String json) throws Exception {
            session.sendMessage(new TextMessage(json));
        }

        void close() throws Exception {
            if (session != null && session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        }
    }
}
