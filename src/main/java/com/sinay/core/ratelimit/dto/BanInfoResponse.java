package com.sinay.core.ratelimit.dto;

import com.sinay.core.ratelimit.service.BanCacheService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Ban bilgisi response DTO'su.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BanInfoResponse {

    private String banKey;
    private boolean isBanned;
    private boolean isPermanent;
    private int banCount;
    private Long remainingMinutes;
    private LocalDateTime banExpiry;
    private LocalDateTime permanentBanAt;
    private Integer strikeCount;
    private LocalDateTime strikeUpdatedAt;

    /**
     * BanCacheService'den dönüştür
     */
    public static BanInfoResponse from(String banKey, BanCacheService.BanInfo info, Integer strikeCount, LocalDateTime strikeUpdatedAt) {
        return BanInfoResponse.builder()
                .banKey(banKey)
                .isBanned(info.isBanned())
                .isPermanent(info.isPermanent())
                .banCount(info.banCount())
                .remainingMinutes(info.isBanned() ? info.remainingMinutes() : null)
                .banExpiry(info.isBanned() && !info.isPermanent() ? info.banExpiry() : null)
                .permanentBanAt(info.isPermanent() ? info.permanentBanAt() : null)
                .strikeCount(strikeCount)
                .strikeUpdatedAt(strikeUpdatedAt)
                .build();
    }

    /**
     * Entity'den dönüştür
     */
    public static BanInfoResponse from(com.sinay.core.ratelimit.entities.RateLimitBan ban) {
        return BanInfoResponse.builder()
                .banKey(ban.getBanKey())
                .isBanned(ban.getIsPermanent() || (ban.getBanExpiry() != null && ban.getBanExpiry().isAfter(java.time.LocalDateTime.now())))
                .isPermanent(ban.getIsPermanent())
                .banCount(ban.getBanCount())
                .remainingMinutes(null) // Hesaplanabilir
                .banExpiry(ban.getBanExpiry())
                .permanentBanAt(ban.getPermanentBanAt())
                .strikeCount(ban.getStrikeCount())
                .strikeUpdatedAt(ban.getStrikeUpdatedAt())
                .build();
    }
}
