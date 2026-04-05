package com.sinay.core.server.cache.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manuel cache işlemleri için servis.
 * <p>
 * Bu servis, anotasyon yerine programatik olarak cache işlemleri yapmak için kullanılır.
 * @Cacheable ve @CacheEvict anotasyonları yeterli olmadığında kullanışlıdır.
 * <p>
 * Kullanım örnekleri:
 * <pre>
 * // Cache'e al
 * cacheService.put("users", userId, userResponse, 300);
 *
 * // Cache'ten oku
 * UserResponse user = cacheService.get("users", userId, UserResponse.class);
 *
 * // Cache'ten sil
 * cacheService.evict("users", userId);
 *
 * // Tüm cache'i temizle
 * cacheService.evictAll("users");
 *
 * // Varlık kontrolü
 * boolean exists = cacheService.exists("users", userId);
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Cache key separator.
     */
    private static final String KEY_SEPARATOR = ":";

    /**
     * Cache'e bir değer ekler.
     *
     * @param cacheName Cache ismi (örn: "users")
     * @param key       Cache anahtarı (örn: UUID)
     * @param value     Cache'e alınacak değer
     * @param ttlSeconds TTL süresi (saniye cinsinden)
     */
    public void put(String cacheName, Object key, Object value, int ttlSeconds) {
        String cacheKey = buildKey(cacheName, key);
        try {
            redisTemplate.opsForValue().set(cacheKey, value, Duration.ofSeconds(ttlSeconds));
            log.debug("Cached: cache={}, key={}, ttl={}s", cacheName, key, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to cache: cache={}, key={}, error={}", cacheName, key, e.getMessage());
        }
    }

    /**
     * Cache'e bir değer ekler (varsayılan TTL ile).
     * <p>
     * Varsayılan TTL: 300 saniye (5 dakika)
     *
     * @param cacheName Cache ismi
     * @param key       Cache anahtarı
     * @param value     Cache'e alınacak değer
     */
    public void put(String cacheName, Object key, Object value) {
        put(cacheName, key, value, 300);
    }

    /**
     * Cache'ten bir değer okur.
     *
     * @param cacheName Cache ismi
     * @param key       Cache anahtarı
     * @param type      Dönüş tipi
     * @param <T>       Generic tip
     * @return Cache'teki değer veya null (cache miss)
     */
    public <T> T get(String cacheName, Object key, Class<T> type) {
        String cacheKey = buildKey(cacheName, key);
        try {
            Object value = redisTemplate.opsForValue().get(cacheKey);
            if (value != null) {
                log.debug("Cache hit: cache={}, key={}", cacheName, key);
                return type.cast(value);
            }
            log.debug("Cache miss: cache={}, key={}", cacheName, key);
            return null;
        } catch (Exception e) {
            log.error("Failed to get from cache: cache={}, key={}, error={}", cacheName, key, e.getMessage());
            return null;
        }
    }

    /**
     * Cache'ten birden fazla değer okur.
     *
     * @param cacheName Cache ismi
     * @param keys      Cache anahtarları
     * @param type      Dönüş tipi
     * @param <T>       Generic tip
     * @return Cache'teki değerler (anahtar -> değer)
     */
    public <T> Map<Object, T> getAll(String cacheName, Collection<Object> keys, Class<T> type) {
        List<String> cacheKeys = keys.stream()
                .map(key -> buildKey(cacheName, key))
                .collect(Collectors.toList());

        try {
            List<Object> values = redisTemplate.opsForValue().multiGet(cacheKeys);

            Map<Object, T> result = new java.util.HashMap<>();
            if (values != null) {
                for (int i = 0; i < keys.size(); i++) {
                    Object key = ((List<Object>) keys).get(i);
                    Object value = values.get(i);
                    if (value != null) {
                        result.put(key, type.cast(value));
                    }
                }
            }

            log.debug("Multi-cache result: cache={}, hits={}, requested={}",
                    cacheName, result.size(), keys.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to get multiple from cache: cache={}, error={}", cacheName, e.getMessage());
            return new java.util.HashMap<>();
        }
    }

    /**
     * Cache'ten belirli bir anahtarı siler.
     *
     * @param cacheName Cache ismi
     * @param key       Silinecek anahtar
     */
    public void evict(String cacheName, Object key) {
        String cacheKey = buildKey(cacheName, key);
        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            log.debug("Evicted: cache={}, key={}, deleted={}", cacheName, key, deleted);
        } catch (Exception e) {
            log.error("Failed to evict: cache={}, key={}, error={}", cacheName, key, e.getMessage());
        }
    }

    /**
     * Cache'ten birden fazla anahtarı siler.
     *
     * @param cacheName Cache ismi
     * @param keys      Silinecek anahtarlar
     */
    public void evictAll(String cacheName, Collection<Object> keys) {
        List<String> cacheKeys = keys.stream()
                .map(key -> buildKey(cacheName, key))
                .collect(Collectors.toList());

        try {
            Long deleted = redisTemplate.delete(cacheKeys);
            log.debug("Evicted multiple: cache={}, count={}", cacheName, deleted);
        } catch (Exception e) {
            log.error("Failed to evict multiple: cache={}, error={}", cacheName, e.getMessage());
        }
    }

    /**
     * Belirli bir cache'deki tüm verileri siler.
     * <p>
     * Dikkat: Bu işlem cache ismiyle eşleşen tüm anahtarları siler.
     *
     * @param cacheName Cache ismi
     */
    public void evictAll(String cacheName) {
        try {
            Set<String> keys = redisTemplate.keys(cacheName + KEY_SEPARATOR + "*");
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                log.info("Evicted all from cache: cache={}, count={}", cacheName, deleted);
            } else {
                log.debug("No keys to evict: cache={}", cacheName);
            }
        } catch (Exception e) {
            log.error("Failed to evict all: cache={}, error={}", cacheName, e.getMessage());
        }
    }

    /**
     * Belirli bir anahtarın cache'te var olup olmadığını kontrol eder.
     *
     * @param cacheName Cache ismi
     * @param key       Kontrol edilecek anahtar
     * @return true varsa, false yoksa
     */
    public boolean exists(String cacheName, Object key) {
        String cacheKey = buildKey(cacheName, key);
        try {
            Boolean exists = redisTemplate.hasKey(cacheKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Failed to check cache existence: cache={}, key={}, error={}",
                    cacheName, key, e.getMessage());
            return false;
        }
    }

    /**
     * Belirli bir cache ismi için tüm anahtarları döndürür.
     *
     * @param cacheName Cache ismi
     * @return Anahtar kümesi
     */
    public Set<String> keys(String cacheName) {
        try {
            return redisTemplate.keys(cacheName + KEY_SEPARATOR + "*");
        } catch (Exception e) {
            log.error("Failed to get cache keys: cache={}, error={}", cacheName, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Cache için tam anahtarı oluşturur.
     * <p>
     * Format: {cacheName}:{key}
     *
     * @param cacheName Cache ismi
     * @param key       Anahtar
     * @return Tam cache anahtarı
     */
    private String buildKey(String cacheName, Object key) {
        return cacheName + KEY_SEPARATOR + key;
    }

    /**
     * Bir değerin TTL'sini (kalan süre) alır.
     *
     * @param cacheName Cache ismi
     * @param key       Anahtar
     * @return Kalan süre (saniye cinsinden) veya -1 (süre yoksa)
     */
    public long getTtl(String cacheName, Object key) {
        String cacheKey = buildKey(cacheName, key);
        try {
            Long expireSeconds = redisTemplate.getExpire(cacheKey);
            return expireSeconds != null ? expireSeconds : -1;
        } catch (Exception e) {
            log.error("Failed to get TTL: cache={}, key={}, error={}", cacheName, key, e.getMessage());
            return -1;
        }
    }

    /**
     * Bir değerin TTL'sini uzatır.
     *
     * @param cacheName  Cache ismi
     * @param key        Anahtar
     * @param ttlSeconds Yeni TTL (saniye cinsinden)
     */
    public void expire(String cacheName, Object key, int ttlSeconds) {
        String cacheKey = buildKey(cacheName, key);
        try {
            redisTemplate.expire(cacheKey, Duration.ofSeconds(ttlSeconds));
            log.debug("Extended TTL: cache={}, key={}, ttl={}s", cacheName, key, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to set TTL: cache={}, key={}, error={}", cacheName, key, e.getMessage());
        }
    }
}
