package com.sinay.core.ratelimit.service;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import com.sinay.core.ratelimit.annotation.RateLimit;
import com.sinay.core.ratelimit.config.RateLimitProperties;
import com.sinay.core.ratelimit.exception.RateLimitException;
import com.sinay.core.ratelimit.key.RateLimitKeyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final BanCacheService banCacheService;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitProperties properties;

    // Bucket cache - her key için ayrı bucket tutar
    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    /**
     * Rate limit kontrolü yap
     */
    public void checkRateLimit(String key, RateLimit rateLimit) {
        // 1. Ban kontrolü
        banCacheService.checkIfBanned(key);

        // 2. Bucket'ı getir veya oluştur
        Bucket bucket = getOrCreateBucket(key, rateLimit);

        // 3. Token tüketmeyi dene
        long tokensToConsume = 1;
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(tokensToConsume);

        // 4. Uyarı threshold kontrolü
        boolean isWarning = isWarningThreshold(probe);

        if (probe.isConsumed()) {
            // Başarılı - strike'ları sıfırlamıyoruz
            // Ban süresi dolduğunda BanCacheService otomatik temizler

            if (isWarning) {
                log.warn("Rate limit warning for key: {}, remaining tokens: {}", key, probe.getRemainingTokens());
            }
            return;
        }

        // 5. Limit aşıldı - strike artır
        int remainingStrikes = banCacheService.incrementStrike(key, rateLimit.banThreshold());

        // Banlandı mı?
        if (remainingStrikes == -1) {
            log.warn("Rate limit ban triggered for key: {}", key);
            // BanCacheService tekrar check edecek, exception fırlatacak
            banCacheService.checkIfBanned(key);
            return;
        }

        // 6. Rate limit exception
        long retryAfterSeconds = Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1;
        log.debug("Rate limit exceeded for key: {}, retry after: {}s, strikes: {}/{}",
                key, retryAfterSeconds, remainingStrikes, rateLimit.banThreshold());

        throw new RateLimitException(
                String.format("Rate limit aşıldı. %d saniye sonra tekrar deneyin. Kalanan deneme: %d/%d",
                        retryAfterSeconds, rateLimit.banThreshold() - remainingStrikes, rateLimit.banThreshold()),
                (int) retryAfterSeconds,
                probe.getRemainingTokens(),
                rateLimit.banThreshold() - remainingStrikes
        );
    }

    private Bucket getOrCreateBucket(String key, RateLimit rateLimit) {
        return bucketCache.computeIfAbsent(key, k -> createBucket(rateLimit));
    }

    private Bucket createBucket(RateLimit rateLimit) {
        Refill refill = Refill.greedy(rateLimit.refillTokens(), Duration.ofMinutes(rateLimit.refillDurationMinutes()));
        Bandwidth limit = Bandwidth.classic(rateLimit.capacity(), refill);
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private boolean isWarningThreshold(ConsumptionProbe probe) {
        int thresholdPercent = properties.getDefaults().getWarningThresholdPercent();
        long remainingTokens = probe.getRemainingTokens();
        long capacity = probe.getRemainingTokens() + 1; // yaklaşık capacity

        return remainingTokens <= (capacity * thresholdPercent / 100);
    }
}
