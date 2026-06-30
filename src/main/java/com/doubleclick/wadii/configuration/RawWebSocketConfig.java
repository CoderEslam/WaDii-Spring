package com.doubleclick.wadii.configuration;

import com.doubleclick.wadii.websocket.ChatWebSocketHandler;
import com.doubleclick.wadii.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class RawWebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(ServerHttpRequest request,
                                              WebSocketHandler wsHandler,
                                              Map<String, Object> attributes) {
                String userId = (String) attributes.get("userId");
                if (userId == null) return null;
                return () -> userId;
            }
        };

        // ws://host/web-socket/{userId}?token=JWT  — plain JSON, no STOMP
        registry.addHandler(chatWebSocketHandler, "/web-socket/*")
                .setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor)
                .setHandshakeHandler(handshakeHandler);
    }
}
