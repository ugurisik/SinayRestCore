package com.sinay.core.websocket.config;

import com.sinay.core.websocket.interceptor.JwtChannelInterceptor;
import com.sinay.core.security.jwt.JwtUtil;
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
     * Client'lar bu endpoint'e bağlanır: ws://host:port/ws
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // CORS - production'da kısıtla
                .withSockJS();  // Fallback for browsers without WebSocket support
    }

    /**
     * JWT authentication interceptor ekle.
     * <p>
     * Her CONNECT isteğinde JWT token doğrulanır.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new JwtChannelInterceptor(jwtUtil));
    }
}
