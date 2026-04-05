package com.sinay.core.server.websocket.config;

import com.sinay.core.server.websocket.interceptor.JwtChannelInterceptor;
import com.sinay.core.server.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket konfigürasyonu.
 * <p>
 * STOMP protocolü kullanılır.
 * JWT authentication ile bağlantı kontrolü yapılır.
 *
 * @see JwtChannelInterceptor
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;

    /**
     * Message broker konfigürasyonu.
     * <p>
     * /topic → Broadcast (tüm abonelere)
     * /queue → User-specific (tek kullanıcıya)
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple memory-based message broker
        config.enableSimpleBroker("/topic", "/queue");

        // Application destination prefix (client'tan gelen mesajlar için)
        config.setApplicationDestinationPrefixes("/app");

        // User destination prefix (tek kullanıcıya gönderim için)
        config.setUserDestinationPrefix("/user");
    }

    /**
     * WebSocket endpoint registration.
     * <p>
     * /ws → JWT token gerekli (production)
     * /ws-test → Authentication yok (geliştirme/test için)
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Production endpoint - JWT auth gerekli
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Test endpoint - Authentication yok
        registry.addEndpoint("/ws-test")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * JWT authentication interceptor ekle.
     * <p>
     * Her CONNECT isteğinde JWT token doğrulanır.
     * Test modu için X-Test-Mode header gönderilebilir.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new JwtChannelInterceptor(jwtUtil));
    }
}
