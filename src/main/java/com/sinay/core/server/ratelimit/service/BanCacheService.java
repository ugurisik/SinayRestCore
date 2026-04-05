package com.sinay.core.server.ratelimit.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sinay.core.server.core.ObjectCore;
import com.sinay.core.server.ratelimit.config.RateLimitProperties;
import com.sinay.core.server.ratelimit.entities.QRateLimitBan;
import com.sinay.core.server.ratelimit.entities.RateLimitBan;
import com.sinay.core.server.ratelimit.exception.RateLimitBanException;
import com.querydsl.core.types.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Rate limit ban servisi.
 * <p>
 * Ban bilgilerini veritabanında tutar (restart sonrası koruma).
 * Cache ile performans optimizasyonu sağlar.
 */
@Slf4j
@Service
@DependsOn("objectCoreInjector")
public class BanCacheService {

    // Kalıcı ban threshold (7. ban'dan sonra kalıcı)
    private static final int PERMANENT_BAN_THRESHOLD = 7;
    // Maksimum ban süresi (24 saat)
    private static final int MAX_BAN_DURATION_MINUTES = 24 * 60;
    // Strike TTL (dakika)
    private static final int STRIKE_TTL_MINUTES = 10;

    private final RateLimitProperties properties;
    private final Cache<String, RateLimitBan> banCache;

    public BanCacheService(RateLimitProperties properties) {
        this.properties = properties;
        this.banCache = Caffeine.newBuilder()
                .maximumSize(properties.getCache().getMaxSize())
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();

        // Başlangıçta kalıcı ban'ları cache'e yükle
        loadPermanentBansIntoCache();
    }

    /**
     * Kullanıcı banlı mı kontrol et.
     * <p>
     * Transaction gerekli çünkü ObjectCore.save() çağrılıyor.
     */
    @Transactional
    public void checkIfBanned(String key) {
        RateLimitBan ban = getOrCreateBanRecord(key);

        // 1. Kalıcı ban kontrolü
        if (Boolean.TRUE.equals(ban.getIsPermanent())) {
            throw new RateLimitBanException(
                    "Kalıcı banlandınız. Rate limit kurallarını sürekli ihlal ettiğiniz için erişiminiz kısıtlandı. Admin ile iletişime geçin.",
                    Integer.MAX_VALUE,
                    LocalDateTime.MAX
            );
        }

        // 2. Geçici ban kontrolü
        LocalDateTime now = LocalDateTime.now();
        if (ban.getBanExpiry() != null && ban.getBanExpiry().isAfter(now)) {
            long remainingMinutes = Duration.between(now, ban.getBanExpiry()).toMinutes();
            throw new RateLimitBanException(
                    String.format("Rate limit nedeniyle %d dakika banlandınız. (Ban sayısı: %d)",
                            remainingMinutes, ban.getBanCount()),
                    (int) remainingMinutes,
                    ban.getBanExpiry()
            );
        }

        // Ban süresi dolmuşsa expiry'yi temizle
        if (ban.getBanExpiry() != null && ban.getBanExpiry().isBefore(now)) {
            ban.setBanExpiry(null);
            ObjectCore.save(ban);
            banCache.put(key, ban);
        }
    }

    /**
     * Strike artır ve ban durumunu kontrol et
     */
    @Transactional
    public int incrementStrike(String key, int banThreshold) {
        RateLimitBan ban = getOrCreateBanRecord(key);
        LocalDateTime now = LocalDateTime.now();

        // Strike sayısını artır
        int newStrikeCount = (ban.getStrikeCount() != null ? ban.getStrikeCount() : 0) + 1;
        ban.setStrikeCount(newStrikeCount);
        ban.setStrikeUpdatedAt(now);

        // Ban threshold'a ulaştı mı?
        if (newStrikeCount >= banThreshold) {
            // Strike'ları sıfırla
            ban.setStrikeCount(0);
            ban.setStrikeUpdatedAt(null);

            // Ban sayısını artır
            int newBanCount = (ban.getBanCount() != null ? ban.getBanCount() : 0) + 1;
            ban.setBanCount(newBanCount);

            // Ban süresini hesapla (exponential: banCount × 10 dakika, max 24 saat)
            int banMinutes = Math.min(newBanCount * 10, MAX_BAN_DURATION_MINUTES);

            // Kalıcı ban kontrolü
            if (newBanCount >= PERMANENT_BAN_THRESHOLD) {
                ban.setIsPermanent(true);
                ban.setPermanentBanAt(now);
                ban.setBanExpiry(null);
                ObjectCore.save(ban);
                banCache.put(key, ban);
                log.error("PERMANENT BAN for key: {} after {} bans", key, newBanCount);
                return -1; // Kalıcı ban
            }

            // Geçici ban
            LocalDateTime banExpiry = now.plusMinutes(banMinutes);
            ban.setBanExpiry(banExpiry);
            ObjectCore.save(ban);
            banCache.put(key, ban);
            log.warn("Rate limit ban triggered for key: {} until {} (Ban count: {}, Duration: {} min)",
                    key, banExpiry, newBanCount, banMinutes);
            return -1; // Banlandı
        }

        // Henüz ban değil, sadece strike arttı
        ObjectCore.save(ban);
        banCache.put(key, ban);
        log.debug("Rate limit strike {}/{} for key: {}", newStrikeCount, banThreshold, key);
        return newStrikeCount;
    }

    /**
     * Mevcut strike sayısını getir
     */
    public int getCurrentStrikes(String key) {
        RateLimitBan ban = getOrCreateBanRecord(key);
        return ban.getStrikeCount() != null ? ban.getStrikeCount() : 0;
    }

    /**
     * Kullanıcının ban bilgisini getir
     */
    public BanInfo getBanInfo(String key) {
        RateLimitBan ban = getOrCreateBanRecord(key);
        LocalDateTime now = LocalDateTime.now();

        // Kalıcı ban
        if (Boolean.TRUE.equals(ban.getIsPermanent())) {
            return new BanInfo(true, true, ban.getBanCount(), Integer.MAX_VALUE,
                    LocalDateTime.MAX, ban.getPermanentBanAt());
        }

        // Geçici ban
        if (ban.getBanExpiry() != null && ban.getBanExpiry().isAfter(now)) {
            long remainingMinutes = Duration.between(now, ban.getBanExpiry()).toMinutes();
            return new BanInfo(true, false, ban.getBanCount(), remainingMinutes,
                    ban.getBanExpiry(), null);
        }

        return BanInfo.notBanned(ban.getBanCount() != null ? ban.getBanCount() : 0);
    }

    /**
     * Admin: Kullanıcının banını kaldır (ban count korunur)
     */
    @Transactional
    public void unban(String key) {
        ObjectCore.Result<RateLimitBan> result = ObjectCore.getByField(RateLimitBan.class, "banKey", key);
        if (result.isSuccess()) {
            RateLimitBan ban = result.getData();
            ban.setBanExpiry(null);
            ban.setStrikeCount(0);
            ban.setStrikeUpdatedAt(null);
            ObjectCore.save(ban);
            banCache.invalidate(key);
            log.info("Ban removed for key: {} (ban count preserved: {})", key, ban.getBanCount());
        }
    }

    /**
     * Admin: Kullanıcının ban sayısını sıfırla (tam affet)
     */
    @Transactional
    public void forgive(String key) {
        ObjectCore.Result<RateLimitBan> result = ObjectCore.getByField(RateLimitBan.class, "banKey", key);
        if (result.isSuccess()) {
            RateLimitBan ban = result.getData();
            ban.setBanCount(0);
            ban.setIsPermanent(false);
            ban.setBanExpiry(null);
            ban.setPermanentBanAt(null);
            ban.setStrikeCount(0);
            ban.setStrikeUpdatedAt(null);
            ObjectCore.save(ban);
            banCache.invalidate(key);
            log.info("User forgiven (all records cleared) for key: {}", key);
        }
    }

    /**
     * Admin: Tüm kalıcı ban'lıları getir
     */
    public List<RateLimitBan> getAllPermanentBans() {
        QRateLimitBan q = QRateLimitBan.rateLimitBan;
        Predicate predicate = q.isPermanent.isTrue().and(q.visible.isTrue());
        ObjectCore.ListResult<RateLimitBan> result = ObjectCore.list(q, predicate, null);
        return result.getData();
    }

    /**
     * Cache'e ban kaydı getir veya yoksa oluştur.
     * <p>
     * Transaction gerekli çünkü ObjectCore.save() çağrılıyor.
     */
    @Transactional
    public RateLimitBan getOrCreateBanRecord(String key) {
        // Önce cache'den kontrol et
        RateLimitBan cached = banCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        // Veritabanından çek
        LocalDateTime now = LocalDateTime.now();
        QRateLimitBan q = QRateLimitBan.rateLimitBan;
        Predicate activePredicate = q.banKey.eq(key)
                .and(q.visible.isTrue())
                .and(q.isPermanent.isTrue()
                        .or(q.banExpiry.isNotNull().and(q.banExpiry.after(now))));

        ObjectCore.Result<RateLimitBan> result = ObjectCore.findOne(q, activePredicate);

        RateLimitBan ban;
        if (result.isSuccess()) {
            ban = result.getData();
        } else {
            // Yeni kayıt oluştur
            ban = RateLimitBan.builder()
                    .banKey(key)
                    .banCount(0)
                    .isPermanent(false)
                    .strikeCount(0)
                    .build();
            ObjectCore.Result<RateLimitBan> saved = ObjectCore.save(ban);
            ban = saved.getData();
        }

        banCache.put(key, ban);
        return ban;
    }

    /**
     * Başlangıçta kalıcı ban'ları cache'e yükle
     */
    private void loadPermanentBansIntoCache() {
        QRateLimitBan q = QRateLimitBan.rateLimitBan;
        Predicate predicate = q.isPermanent.isTrue().and(q.visible.isTrue());
        ObjectCore.ListResult<RateLimitBan> result = ObjectCore.list(q, predicate, null);
        List<RateLimitBan> permanentBans = result.getData();

        for (RateLimitBan ban : permanentBans) {
            banCache.put(ban.getBanKey(), ban);
        }
        log.info("Loaded {} permanent bans into cache", permanentBans.size());
    }

    /**
     * Her saat eski ban kayıtlarını temizle (soft delete)
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupOldBans() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30); // 30 günden eski
        QRateLimitBan q = QRateLimitBan.rateLimitBan;
        Predicate predicate = q.updatedAt.before(cutoff)
                .and(q.isPermanent.isFalse())
                .and(q.banExpiry.isNull().or(q.banExpiry.before(LocalDateTime.now())))
                .and(q.visible.isTrue());

        ObjectCore.ListResult<RateLimitBan> result = ObjectCore.list(q, predicate, null);
        List<RateLimitBan> oldBans = result.getData();

        for (RateLimitBan ban : oldBans) {
            ObjectCore.delete(ban);
            banCache.invalidate(ban.getBanKey());
        }
        if (!oldBans.isEmpty()) {
            log.info("Cleaned up {} old ban records", oldBans.size());
        }
    }

    /**
     * Ban bilgisi DTO'su
     */
    public record BanInfo(
            boolean isBanned,
            boolean isPermanent,
            int banCount,
            long remainingMinutes,
            LocalDateTime banExpiry,
            LocalDateTime permanentBanAt
    ) {
        public static BanInfo notBanned(int banCount) {
            return new BanInfo(false, false, banCount, 0, null, null);
        }
    }
}
