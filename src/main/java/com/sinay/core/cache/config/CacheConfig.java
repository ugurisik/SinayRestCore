package com.sinay.core.cache.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis tabanlı Spring Cache konfigürasyonu.
 * <p>
 * Bu konfigürasyon sınıfı şu özellikleri sağlar:
 * <ul>
 *   <li>Redis ile cache yönetimi</li>
 *   <li>JSON serileştirme (Jackson)</li>
 *   <li>Özel cache key generator</li>
 *   <li>Cache hata yönetimi</li>
 *   <li>TTL (time-to-live) desteği</li>
 * </ul>
 * <p>
 * Kullanım:
 * <pre>
 * &#64;Cacheable(value = "users", key = "#id", ttl = 300)
 * public UserResponse getById(UUID id) { }
 * </pre>
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    /**
     * Varsayılan cache TTL'si (saniye cinsinden).
     * 5 dakika = 300 saniye
     */
    private static final int DEFAULT_TTL_SECONDS = 300;

    /**
     * Cache için ObjectMapper bean'i.
     * <p>
     * JSON serileştirme için kullanılır. Java 8 tarih/saat tiplerini destekler.
     *
     * @return JSON serileştirme için yapılandırılmış ObjectMapper
     */
    @Bean
    public ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        // Type bilgisi için_polymorphism desteği
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // Private field'lara erişim
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        log.debug("Cache ObjectMapper configured");
        return mapper;
    }

    /**
     * Redis için yapılandırılmış RedisTemplate.
     * <p>
     * Manuel cache işlemleri için kullanılır.
     *
     * @param connectionFactory Redis bağlantı fabrikası
     * @return Yapılandırılmış RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // String serializer için key'ler
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // JSON serializer için value'lar
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(cacheObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        log.debug("RedisTemplate configured");
        return template;
    }

    /**
     * Spring Cache için RedisCacheManager.
     * <p>
     * @Cacheable ve @CacheEvict anotasyonları için cache yöneticisi.
     *
     * @param connectionFactory Redis bağlantı fabrikası
     * @return Yapılandırılmış CacheManager
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // JSON serializer - Spring 6+ için
        RedisSerializationContext.SerializationPair<Object> jsonSerializer =
                RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(cacheObjectMapper())
                );

        // Varsayılan cache konfigürasyonu
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(DEFAULT_TTL_SECONDS))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )
                .serializeValuesWith(jsonSerializer)
                .disableCachingNullValues();

        // Cache-specific konfigürasyonlar
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Kullanıcı cache'i - 10 dakika
        cacheConfigurations.put("users",
                defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // Rate limit cache'i - 1 dakika
        cacheConfigurations.put("rateLimits",
                defaultConfig.entryTtl(Duration.ofMinutes(1)));

        // Ban cache'i - 15 dakika
        cacheConfigurations.put("bans",
                defaultConfig.entryTtl(Duration.ofMinutes(15)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();

        log.info("RedisCacheManager configured with {} custom caches", cacheConfigurations.size());
        return cacheManager;
    }

    /**
     * Özel cache key generator.
     * <p>
     * Key formatı: {className}:{methodName}:{param1}:{param2}
     * <p>
     * Örnek: "UserService:getById:550e8400-e29b-41d4-a716-446655440000"
     *
     * @return Spring için key generator bean'i
     */
    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder key = new StringBuilder();

            // Class adı (basit)
            String className = target.getClass().getSimpleName();
            if (className.endsWith("$$SpringCGLIB$$")) {
                className = className.substring(0, className.indexOf("$$"));
            }
            key.append(className);

            // Method adı
            key.append(":").append(method.getName());

            // Parametreler
            for (Object param : params) {
                if (param != null) {
                    key.append(":").append(param.toString());
                } else {
                    key.append(":null");
                }
            }

            String generatedKey = key.toString();
            log.trace("Generated cache key: {}", generatedKey);
            return generatedKey;
        };
    }

    /**
     * Cache hata yöneticisi.
     * <p>
     * Cache hataları uygulamanın çalışmasını engellemesin diye log yazar ve devam eder.
     *
     * @return Cache error handler
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.error("Cache GET error: cache={}, key={}, error={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, Object value) {
                log.error("Cache PUT error: cache={}, key={}, error={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.error("Cache EVICT error: cache={}, key={}, error={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
                log.error("Cache CLEAR error: cache={}, error={}",
                        cache.getName(), exception.getMessage());
            }
        };
    }
}
