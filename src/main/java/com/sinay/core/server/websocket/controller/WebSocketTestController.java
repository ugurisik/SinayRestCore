package com.sinay.core.server.websocket.controller;

import com.sinay.core.server.dto.response.ApiResponse;
import com.sinay.core.server.websocket.dto.WebSocketMessage;
import com.sinay.core.server.websocket.enums.MessageType;
import com.sinay.core.server.websocket.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * WebSocket test controller'ı.
 * <p>
 * REST endpoint'lerden WebSocket mesajı göndermek için kullanılır.
 * Production'da kaldırılabilir veya admin-only yapılabilir.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/websocket")
@RequiredArgsConstructor
public class WebSocketTestController {

    private final WebSocketService webSocketService;

    /**
     * Tüm kullanıcılara broadcast gönderir.
     *
     * @param body { "type": "NOTIFICATION", "content": "...", "title": "..." }
     */
    @PostMapping("/broadcast")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> broadcast(@RequestBody Map<String, String> body) {
        String typeStr = body.getOrDefault("type", "NOTIFICATION");
        String content = body.get("content");
        String title = body.get("title");

        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("Content boş olamaz"));
        }

        MessageType type = MessageType.valueOf(typeStr.toUpperCase());

        WebSocketMessage message = WebSocketMessage.builder()
                .type(type)
                .content(content)
                .title(title)
                .build();

        webSocketService.broadcast(message);

        log.info("Broadcast gönderildi: type={}, content={}", type, content);

        return ResponseEntity.ok(ApiResponse.ok(null, "Broadcast gönderildi"));
    }

    /**
     * Belirli bir kullanıcıya mesaj gönderir.
     *
     * @param userId Hedef kullanıcı ID
     * @param body   { "type": "CHAT", "content": "..." }
     */
    @PostMapping("/send/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> sendToUser(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> body) {

        String typeStr = body.getOrDefault("type", "CHAT");
        String content = body.get("content");
        String title = body.get("title");

        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("Content boş olamaz"));
        }

        MessageType type = MessageType.valueOf(typeStr.toUpperCase());

        webSocketService.sendToUser(userId, type, content, title);

        log.info("Kullanıcıya mesaj gönderildi: userId={}, type={}", userId, type);

        return ResponseEntity.ok(ApiResponse.ok(null, "Mesaj gönderildi"));
    }

    /**
     * Sistem mesajı gönderir.
     *
     * @param body { "message": "..." }
     */
    @PostMapping("/system")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> sendSystemMessage(@RequestBody Map<String, String> body) {
        String message = body.get("message");

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("Message boş olamaz"));
        }

        webSocketService.sendSystemMessage(message);

        log.info("Sistem mesajı gönderildi: {}", message);

        return ResponseEntity.ok(ApiResponse.ok(null, "Sistem mesajı gönderildi"));
    }

    /**
     * Alert gönderir.
     *
     * @param body { "title": "...", "content": "..." }
     */
    @PostMapping("/alert")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> sendAlert(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        String content = body.get("content");

        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("Title boş olamaz"));
        }

        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("Content boş olamaz"));
        }

        webSocketService.sendAlert(title, content);

        log.info("Alert gönderildi: title={}, content={}", title, content);

        return ResponseEntity.ok(ApiResponse.ok(null, "Alert gönderildi"));
    }

    /**
     * Data update bildirimi gönderir.
     *
     * @param body { "entityType": "User", "entityId": "...", "action": "UPDATE" }
     */
    @PostMapping("/data-update")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> sendDataUpdate(@RequestBody Map<String, String> body) {
        String entityType = body.get("entityType");
        String entityIdStr = body.get("entityId");
        String action = body.get("action");

        if (entityType == null || entityType.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("EntityType boş olamaz"));
        }

        if (entityIdStr == null || entityIdStr.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("EntityId boş olamaz"));
        }

        UUID entityId = UUID.fromString(entityIdStr);

        webSocketService.sendDataUpdate(entityType, entityId, action);

        log.info("Data update gönderildi: entityType={}, entityId={}, action={}",
                entityType, entityId, action);

        return ResponseEntity.ok(ApiResponse.ok(null, "Data update gönderildi"));
    }
}
