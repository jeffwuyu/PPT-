package com.pptagent.collab.websocket;

import com.pptagent.collab.security.CollaborationAccessChecker;
import com.pptagent.collab.security.CollaborationAuthenticator;
import com.pptagent.collab.security.CollaborationUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

@Component
/**
 * WebSocket 握手鉴权入口。
 *
 * <p>HTTP Security 对 `/ws/presentations/**` 放行，是为了让 Upgrade 请求
 * 能进入 Spring WebSocket；真正的 token 解析、用户加载和 Presentation
 * 读取权限校验都在这里完成。握手失败时直接返回 400/401/403，连接不会建立。</p>
 */
public class CollaborationHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_PRESENTATION_ID = "presentationId";
    static final String ATTR_USER = "collaborationUser";

    private static final String BEARER_PREFIX = "Bearer ";

    private final CollaborationAuthenticator authenticator;
    private final CollaborationAccessChecker accessChecker;

    public CollaborationHandshakeInterceptor(
            CollaborationAuthenticator authenticator,
            CollaborationAccessChecker accessChecker
    ) {
        this.authenticator = authenticator;
        this.accessChecker = accessChecker;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        Long presentationId = extractPresentationId(request);
        if (presentationId == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        String token = extractToken(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Optional<CollaborationUser> user = authenticator.authenticate(token);
        if (user.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        /*
         * 连接、presence、cursor、sync 都至少要求读权限；写权限留给
         * yjs-update 和 lock 操作在消息处理阶段二次判断。
         */
        if (!accessChecker.canViewPresentation(user.get().id(), presentationId)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        attributes.put(ATTR_PRESENTATION_ID, presentationId);
        attributes.put(ATTR_USER, user.get());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }

    private Long extractPresentationId(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash == path.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(path.substring(slash + 1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        String query = request.getURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int index = pair.indexOf('=');
            if (index > 0 && "access_token".equals(pair.substring(0, index))) {
                return pair.substring(index + 1);
            }
        }
        return null;
    }
}
