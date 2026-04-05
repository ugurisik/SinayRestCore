package com.sinay.core.server.ratelimit.entities;

import com.sinay.core.server.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Rate limit ban kaydı.
 * <p>
 * Kullanıcıların ban durumunu, ban sayısını ve ban sürelerini veritabanında tutar.
 * Böylece uygulama restart olsa bile ban bilgileri korunur.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "rate_limit_bans", indexes = {
        @Index(name = "idx_ban_key", columnList = "ban_key"),
        @Index(name = "idx_permanent", columnList = "is_permanent"),
        @Index(name = "idx_ban_expiry", columnList = "ban_expiry"),
        @Index(name = "idx_visible", columnList = "visible")
})
public class RateLimitBan extends BaseEntity {

    /**
     * Ban anahtarı (user UUID, IP adresi veya API key)
     */
    @Column(name = "ban_key", nullable = false, length = 255)
    private String banKey;

    /**
     * Toplam ban sayısı (her ban'da artar)
     */
    @Column(name = "ban_count", nullable = false)
    private Integer banCount = 0;

    /**
     * Kalıcı ban mı?
     */
    @Column(name = "is_permanent", nullable = false)
    private Boolean isPermanent = false;

    /**
     * Geçici ban bitiş zamanı
     */
    @Column(name = "ban_expiry")
    private LocalDateTime banExpiry;

    /**
     * Kalıcı ban başlangıç zamanı
     */
    @Column(name = "permanent_ban_at")
    private LocalDateTime permanentBanAt;

    /**
     * Son strike sayısı (rate limit_threshold kontrolü için)
     */
    @Column(name = "strike_count")
    private Integer strikeCount = 0;

    /**
     * Strike'ların son güncellenme zamanu
     */
    @Column(name = "strike_updated_at")
    private LocalDateTime strikeUpdatedAt;
}
