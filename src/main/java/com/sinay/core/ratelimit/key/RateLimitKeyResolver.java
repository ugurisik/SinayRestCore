package com.sinay.core.ratelimit.key;

import com.sinay.core.ratelimit.model.KeyType;
import com.sinay.core.security.userdetails.AppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Slf4j
@Component
public class RateLimitKeyResolver {

    /**
     * KeyType'a göre rate limit key oluştur
     * <p>
     * Request context kullanılamıyorsa null döner.
     *
     * @param keyType Rate limit key tipi
     * @return Oluşturulan key veya null (request context yoksa)
     */
    public String resolveKey(KeyType keyType) {
        HttpServletRequest request = getCurrentRequest();

        if (request == null) {
            log.warn("Request context not found for key type: {}", keyType);
            return null;
        }

        return switch (keyType) {
            case IP -> getClientIp(request);
            case USER -> getAuthenticatedUser();
            case IP_AND_USER -> {
                String ip = getClientIp(request);
                String user = getAuthenticatedUser();
                // userId:ip formatında (örn: "550e8400-29b-41d4-a716-446655440000:192.168.1.1")
                yield user != null ? user + "@" + ip : ip;
            }
            case API_KEY -> {
                String apiKey = request.getHeader("X-API-Key");
                yield apiKey != null ? "api:" + apiKey : getClientIp(request);
            }
        };
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String getAuthenticatedUser() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof AppUserDetails appUserDetails) {
                UUID userId = appUserDetails.getId();
                return userId != null ? userId.toString() : null;
            }
            // Fallback: username kullan
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;  // Exception yerine null döndür, resolveKey() içinde handle edilsin
        }
        return attributes.getRequest();
    }
}
