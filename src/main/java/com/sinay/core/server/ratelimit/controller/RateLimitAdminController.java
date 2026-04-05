package com.sinay.core.server.ratelimit.controller;

import com.sinay.core.server.dto.response.ApiResponse;
import com.sinay.core.server.ratelimit.dto.BanInfoResponse;
import com.sinay.core.server.ratelimit.entities.RateLimitBan;
import com.sinay.core.server.ratelimit.service.BanCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Rate limit admin controller.
 * <p>
 * Ban yönetimi için admin endpoint'leri. Sadece MASTER_ADMIN erişebilir.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ratelimit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER_ADMIN')")
public class RateLimitAdminController {

    private final BanCacheService banCacheService;

    /**
     * Kullanıcının ban bilgisini getir
     */
    @GetMapping("/ban-info")
    public ResponseEntity<ApiResponse<BanInfoResponse>> getBanInfo(@RequestParam("key") String key) {
        log.info("Admin fetching ban info for key: {}", key);

        // Ban bilgisini al
        BanCacheService.BanInfo info = banCacheService.getBanInfo(key);

        // Strike count'ı da ekleyelim (repository'den çekmek gerekir ama burada basit tutuyoruz)
        BanInfoResponse response = BanInfoResponse.from(key, info, null, null);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Kullanıcının banını kaldır (ban count korunur)
     */
    @PostMapping("/unban")
    public ResponseEntity<ApiResponse<Void>> unban(@RequestParam("key") String key) {
        log.info("Admin removing ban for key: {}", key);
        banCacheService.unban(key);
        return ResponseEntity.ok(ApiResponse.ok(null, "Ban kaldırıldı (ban count korunuyor)"));
    }

    /**
     * Kullanıcının tüm ban geçmişini sil (tam affet)
     */
    @PostMapping("/forgive")
    public ResponseEntity<ApiResponse<Void>> forgive(@RequestParam("key") String key) {
        log.info("Admin forgiving all bans for key: {}", key);
        banCacheService.forgive(key);
        return ResponseEntity.ok(ApiResponse.ok(null, "Kullanıcı affedildi, tüm kayıtlar silindi"));
    }

    /**
     * Tüm kalıcı ban'lı kullanıcıları getir
     */
    @GetMapping("/permanent-bans")
    public ResponseEntity<ApiResponse<List<BanInfoResponse>>> getPermanentBans() {
        log.info("Admin fetching all permanent bans");

        List<RateLimitBan> permanentBans = banCacheService.getAllPermanentBans();
        List<BanInfoResponse> responses = permanentBans.stream()
                .map(BanInfoResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(responses));
    }
}
