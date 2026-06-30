package com.doubleclick.wadii.websocket;

import com.doubleclick.wadii.auth.config.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String token = extractTokenFromQuery(request.getURI().getQuery());
        if (token == null) return false;
        try {
            String email = jwtUtil.extractEmail(token);
            String userIdFromToken = jwtUtil.extractId(token);
            if (email == null || userIdFromToken == null || !jwtUtil.validateToken(token, email)) {
                return false;
            }
            // If userId is present in the path (/web-socket/{userId}), cross-validate with token
            String userIdFromPath = extractUserIdFromPath(request.getURI().getPath());
            if (userIdFromPath != null && !userIdFromPath.equals(userIdFromToken)) {
                return false;
            }
            attributes.put("userId", userIdFromToken);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }

    private String extractTokenFromQuery(String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }

    /**
     * Extracts the userId segment from paths like /web-socket/42 or /web-socket/42/...
     */
    private String extractUserIdFromPath(String path) {
        if (path == null) return null;
        String[] segments = path.split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if (segments[i].equals("web-socket")) {
                String candidate = segments[i + 1];
                if (!candidate.isEmpty() && candidate.matches("\\d+")) {
                    return candidate;
                }
                break;
            }
        }
        return null;
    }
}