package com.sinay.core.server.cache.aspect;

import com.sinay.core.server.cache.annotation.CacheEvict;
import com.sinay.core.server.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache anotasyonları için AOP aspect.
 * <p>
 * Bu aspect şu anotasyonları işler:
 * <ul>
 *   <li>{@link Cacheable} - Metod sonucunu cache'e alır</li>
 *   <li>{@link CacheEvict} - Cache'ten değer siler</li>
 * </ul>
 * <p>
 * Cache işlemleri Redis üzerinde yapılır.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class CacheAspect {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * Cache key separator.
     */
    private static final String KEY_SEPARATOR = ":";

    /**
     * SpEL evaluation context cache (performans için).
     */
    private final ConcurrentHashMap<String, EvaluationContext> contextCache = new ConcurrentHashMap<>();

    /**
     * @Cacheable anotasyonunu işler.
     * <p>
     * Metod çalıştırılmadan önce cache'e bakar. Cache'te varsa, metodu
     * çalıştırmadan cache'teki değeri döndürür.
     */
    @Around("@annotation(cacheable)")
    public Object handleCacheable(ProceedingJoinPoint joinPoint, Cacheable cacheable) throws Throwable {
        String cacheName = cacheable.value();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Cache key oluştur
        String cacheKey = buildCacheKey(cacheName, cacheable.key(), method, joinPoint.getArgs());

        // Cache'te var mı kontrol et
        try {
            Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
            if (cachedValue != null) {
                log.debug("Cache hit: cache={}, key={}", cacheName, cacheKey);
                return cachedValue;
            }
        } catch (Exception e) {
            log.error("Cache read error: cache={}, key={}, error={}", cacheName, cacheKey, e.getMessage());
        }

        // Cache'te yoksa, metodu çalıştır
        log.debug("Cache miss: cache={}, key={}", cacheName, cacheKey);
        Object result = joinPoint.proceed();

        // Sonucu cache'e al (koşullar sağlanıyorsa)
        if (shouldCache(result, cacheable.condition(), cacheable.unless(), method, joinPoint.getArgs())) {
            try {
                int ttl = cacheable.ttl();
                if (ttl > 0) {
                    redisTemplate.opsForValue().set(cacheKey, result, Duration.ofSeconds(ttl));
                    log.debug("Cached: cache={}, key={}, ttl={}s", cacheName, cacheKey, ttl);
                } else {
                    redisTemplate.opsForValue().set(cacheKey, result);
                    log.debug("Cached (no expiry): cache={}, key={}", cacheName, cacheKey);
                }
            } catch (Exception e) {
                log.error("Cache write error: cache={}, key={}, error={}", cacheName, cacheKey, e.getMessage());
            }
        }

        return result;
    }

    /**
     * @CacheEvict anotasyonunu işler.
     * <p>
     * Metod çalıştırıldıktan sonra (veya önce, eğer beforeInvocation = true) cache'i temizler.
     */
    @Around("@annotation(cacheEvict)")
    public Object handleCacheEvict(ProceedingJoinPoint joinPoint, CacheEvict cacheEvict) throws Throwable {
        String cacheName = cacheEvict.value();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // beforeInvocation = true ise, metottan önce sil
        if (cacheEvict.beforeInvocation()) {
            performEvict(cacheName, cacheEvict, method, joinPoint.getArgs());
        }

        // Metodu çalıştır
        Object result = joinPoint.proceed();

        // beforeInvocation = false ise, metottan sonra sil
        if (!cacheEvict.beforeInvocation()) {
            performEvict(cacheName, cacheEvict, method, joinPoint.getArgs());
        }

        return result;
    }

    /**
     * Cache temizleme işlemini gerçekleştirir.
     */
    private void performEvict(String cacheName, CacheEvict cacheEvict, Method method, Object[] args) {
        try {
            // Koşul kontrolü
            if (evaluateCondition(cacheEvict.condition(), method, args, null)) {
                if (cacheEvict.allEntries()) {
                    // Tüm cache'i temizle
                    evictAll(cacheName);
                } else {
                    // Belirli anahtarı temizle
                    String cacheKey = buildCacheKey(cacheName, cacheEvict.key(), method, args);
                    redisTemplate.delete(cacheKey);
                    log.debug("Evicted: cache={}, key={}", cacheName, cacheKey);
                }
            }
        } catch (Exception e) {
            log.error("Cache evict error: cache={}, error={}", cacheName, e.getMessage());
        }
    }

    /**
     * Tüm cache'i temizler.
     */
    private void evictAll(String cacheName) {
        try {
            var keys = redisTemplate.keys(cacheName + KEY_SEPARATOR + "*");
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                log.info("Evicted all: cache={}, count={}", cacheName, deleted);
            } else {
                log.debug("No keys to evict: cache={}", cacheName);
            }
        } catch (Exception e) {
            log.error("Cache evict all error: cache={}, error={}", cacheName, e.getMessage());
        }
    }

    /**
     * Cache anahtarı oluşturur.
     */
    private String buildCacheKey(String cacheName, String keyExpression, Method method, Object[] args) {
        if (keyExpression == null || keyExpression.isEmpty()) {
            // Varsayılan key: cacheName:className:methodName:param1:param2
            StringBuilder key = new StringBuilder(cacheName);
            key.append(KEY_SEPARATOR).append(method.getDeclaringClass().getSimpleName());
            key.append(KEY_SEPARATOR).append(method.getName());

            for (Object arg : args) {
                key.append(KEY_SEPARATOR).append(arg != null ? arg.toString() : "null");
            }

            return key.toString();
        } else {
            // SpEL expression'ı değerlendir
            String evaluatedKey = evaluateExpression(keyExpression, method, args, String.class);
            return cacheName + KEY_SEPARATOR + evaluatedKey;
        }
    }

    /**
     * Değerin cache'lenip cache'lenmeyeceğini kontrol eder.
     */
    private boolean shouldCache(Object result, String condition, String unless, Method method, Object[] args) {
        // Condition kontrolü
        if (!condition.isEmpty()) {
            if (!evaluateCondition(condition, method, args, result)) {
                log.debug("Cache skipped: condition false");
                return false;
            }
        }

        // Unless kontrolü
        if (!unless.isEmpty()) {
            if (evaluateCondition(unless, method, args, result)) {
                log.debug("Cache skipped: unless true");
                return false;
            }
        }

        return true;
    }

    /**
     * SpEL expression'ı değerlendirir (boolean sonuc).
     */
    private boolean evaluateCondition(String expressionString, Method method, Object[] args, Object result) {
        try {
            Boolean value = evaluateExpression(expressionString, method, args, Boolean.class, result);
            return Boolean.TRUE.equals(value);
        } catch (Exception e) {
            log.error("Condition evaluation error: expr={}, error={}", expressionString, e.getMessage());
            return true; // Hata durumunda cache'leme devam etsin
        }
    }

    /**
     * SpEL expression'ı değerlendirir.
     */
    private <T> T evaluateExpression(String expressionString, Method method, Object[] args, Class<T> returnType) {
        return evaluateExpression(expressionString, method, args, returnType, null);
    }

    /**
     * SpEL expression'ı değerlendirir (result ile).
     */
    private <T> T evaluateExpression(String expressionString, Method method, Object[] args,
                                      Class<T> returnType, Object result) {
        try {
            Expression expression = parser.parseExpression(expressionString);
            EvaluationContext context = createEvaluationContext(method, args, result);
            return expression.getValue(context, returnType);
        } catch (Exception e) {
            log.error("Expression evaluation error: expr={}, error={}", expressionString, e.getMessage());
            throw e;
        }
    }

    /**
     * SpEL evaluation context oluşturur.
     */
    private EvaluationContext createEvaluationContext(Method method, Object[] args, Object result) {
        String cacheKey = method.toString() + (result != null ? result.hashCode() : "");
        return contextCache.computeIfAbsent(cacheKey, k -> {
            StandardEvaluationContext context = new StandardEvaluationContext();

            // Parametreleri ekle (#p0, #p1, ...)
            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
                context.setVariable("a" + (i + 1), args[i]); // #a1, #a2 alternatifi
            }

            // Parametre isimlerini ekle (eğer varsa)
            String[] parameterNames = getParameterNames(method); // Basit implementasyon
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }

            // Result ekle (metod sonrası için)
            if (result != null) {
                context.setVariable("result", result);
            }

            // Root objeleri ekle
            context.setVariable("rootMethod", method.getName());
            context.setVariable("rootArgs", args);

            return context;
        });
    }

    /**
     * Metod parametre isimlerini döndürür (basit implementasyon).
     * <p>
     * Not: Gerçek uygulamada Spring'in ParameterNameDiscoverer kullanılmalı.
     */
    private String[] getParameterNames(Method method) {
        int paramCount = method.getParameterCount();
        String[] names = new String[paramCount];
        for (int i = 0; i < paramCount; i++) {
            names[i] = "arg" + i;
        }
        return names;
    }
}
