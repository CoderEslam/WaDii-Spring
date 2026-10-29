package com.doubleclick.wadii.chat.ws;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.TokenAuthenticator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.util.Map;

/**
 * STOMP over WebSocket. Endpoints: {@code /ws} (SockJS, web) and {@code /ws-native} (plain, mobile).
 * The JWT is taken from {@code ?token=} at handshake or from the STOMP CONNECT {@code Authorization} header.
 * The principal name is the user id, so {@code /user/queue/...} destinations address users by id.
 * Clients can only SUBSCRIBE to their own {@code /user/queue/*} destinations; {@code /topic} is not exposed.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String USER_ATTR = "wadii.user";

    private final TokenAuthenticator tokenAuthenticator;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue");
        config.setUserDestinationPrefix("/user");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        HandshakeInterceptor tokenInterceptor = new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                           Map<String, Object> attributes) {
                String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
                if (token == null) return true; // may authenticate on CONNECT instead
                try {
                    attributes.put(USER_ATTR, tokenAuthenticator.authenticate(token));
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception ex) {
            }
        };
        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(ServerHttpRequest request, WebSocketHandler handler, Map<String, Object> attributes) {
                Object user = attributes.get(USER_ATTR);
                return user instanceof AuthUser u ? new StompPrincipal(u) : null;
            }
        };
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*")
                .addInterceptors(tokenInterceptor).setHandshakeHandler(handshakeHandler).withSockJS();
        registry.addEndpoint("/ws-native").setAllowedOriginPatterns("*")
                .addInterceptors(tokenInterceptor).setHandshakeHandler(handshakeHandler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) return message;
                if (accessor.getCommand() == StompCommand.CONNECT && accessor.getUser() == null) {
                    String header = accessor.getFirstNativeHeader("Authorization");
                    if (header == null || !header.startsWith("Bearer ")) {
                        throw new IllegalArgumentException("Authentication required");
                    }
                    accessor.setUser(new StompPrincipal(tokenAuthenticator.authenticate(header.substring(7).trim())));
                }
                if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
                    String destination = accessor.getDestination();
                    if (accessor.getUser() == null || destination == null || !destination.startsWith("/user/queue/")) {
                        throw new IllegalArgumentException("Subscriptions are limited to /user/queue/*");
                    }
                }
                if ((accessor.getCommand() == StompCommand.SEND) && accessor.getUser() == null) {
                    throw new IllegalArgumentException("Authentication required");
                }
                return message;
            }
        });
    }

    /** Principal whose name is the user id. */
    public record StompPrincipal(AuthUser user) implements Principal {
        @Override
        public String getName() {
            return String.valueOf(user.id());
        }
    }
}
