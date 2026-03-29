package com.sinay.core.websocket.interceptor;

import com.sinay.core.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WebSocket JWT authentication interceptor.
 * <p>
 * Her STOMP CONNECT isteğinde JWT token doğrular.
 * Token geçersizse bağlantı reddedilir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // Sadece CONNECT komutunu doğrula
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);

            if (token != null && jwtUtil.isTokenValid(token)) {
                // Token valid - kullanıcı bilgisini al
                String username = jwtUtil.extractUsername(token);

                // Spring Security context'e kullanıcıyı set et
                User principal = new User(username, "", List.of());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, List.of());

                accessor.setUser(authentication);

                log.debug("WebSocket bağlantısı doğrulandı: username={}", username);

            } else {
                log.warn("WebSocket bağlantısı reddedildi: Geçersiz token");
                throw new IllegalArgumentException("Geçersiz JWT token");
            }
        }

        return message;
    }

    /**
     * STOMP header'larından JWT token'ı çıkarır.
     * <p>
     * Sırayla kontrol eder:
     * <ol>
     *   <li>Authorization header → Bearer {token}</li>
     *   <li>token header → {token}</li>
     *   <li>query param → ?token={token}</li>
     * </ol>
     */
    private String extractToken(StompHeaderAccessor accessor) {
        // 1. Authorization header
        List<String> authHeaders = accessor.getNativeHeader(AUTH_HEADER);
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (authHeader.startsWith(BEARER_PREFIX)) {
                return authHeader.substring(BEARER_PREFIX.length());
            }
        }

        // 2. token header (fallback)
        List<String> tokenHeaders = accessor.getNativeHeader("token");
        if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
            return tokenHeaders.get(0);
        }

        // 3. Native session attributes (query param)
        Object tokenAttr = accessor.getSessionAttributes().get("token");
        if (tokenAttr instanceof String) {
            return (String) tokenAttr;
        }

        return null;
    }
}
