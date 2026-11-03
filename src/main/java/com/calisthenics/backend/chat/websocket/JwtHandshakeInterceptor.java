package com.calisthenics.backend.chat.websocket;

import com.calisthenics.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return false;
        }

        Long userId = jwtTokenProvider.getUserIdFromJWT(token);
        Principal principal = () -> String.valueOf(userId);
        attributes.put("userId", userId);
        attributes.put("principal", principal);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No-op; handshake is validated above.
    }

    private String extractToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }

        return request.getURI().getQuery() != null && request.getURI().getQuery().contains("token=")
                ? request.getURI().getQuery().replaceFirst(".*token=", "").split("&")[0]
                : null;
    }
}
