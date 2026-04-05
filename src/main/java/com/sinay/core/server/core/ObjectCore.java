package com.sinay.core.server.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.PathBuilder;
import com.sinay.core.server.audit.enums.AuditAction;
import com.sinay.core.server.audit.event.AuditEvent;
import com.sinay.core.server.base.BaseEntity;
import com.sinay.core.server.timetest.annotation.TimeTest;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * ObjectCore - Modern Spring Boot 3+ static utility class.
 * <p>
 * Eski Hibernate 3 ObjectCore'un modern versiyonu.
 * Tüm database operasyonları için tek noktadan erişim.
 */
public final class ObjectCore {

    private static final Logger log = LoggerFactory.getLogger(ObjectCore.class);

    private ObjectCore() {
        // Static utility class - instance yok
    }

    // ===== STATIC FIELD'LER (Spring injection ile set edilir) =====

    /**
     * EntityManager - ThreadLocal ile thread-safe caching.
     * <p>
     * Spring'in EntityManager proxy'si zaten thread-safe, ama her thread için
     * bir kere ApplicationContext'ten alıp cache'leriz (performans için).
     */
    private static final ThreadLocal<EntityManager> entityManagerHolder = new ThreadLocal<>();

    private static ObjectMapper objectMapper;
    private static org.springframework.context.ApplicationContext applicationContext;
    private static com.querydsl.jpa.impl.JPAQueryFactory queryFactory;

    /**
     * Basit (serializable) tipler seti.
     * <p>
     * Audit log için serialize edilebilir tipler.
     * O(1) lookup performansı için Set kullanılır.
     */
    private static final Set<Class<?>> SIMPLE_TYPES = Set.of(
        // String
        String.class,

        // Primitive wrapper'lar
        Integer.class, Long.class, Double.class, Float.class,
        Short.class, Byte.class, Boolean.class, Character.class,

        // Primitive'lar
        int.class, long.class, double.class, float.class,
        short.class, byte.class, boolean.class, char.class,

        // UUID
        UUID.class,

        // Date/Time
        java.time.LocalDateTime.class,
        java.time.LocalDate.class,
        java.time.LocalTime.class,
        java.time.ZonedDateTime.class,
        java.util.Date.class
    );

    /**
     * Lookup tabloları için in-memory cache.
     * <p>
     * Küçük ve sık değişmeyen tablolar için (şehir, kategori, ülke vb.).
     * Caffeine cache kullanılır (high-performance Java caching library).
     * <p>
     * Cache ayarları:
     * <ul>
     *   <li>Maximum size: 1000 entity</li>
     *   <li>TTL: 10 dakika</li>
     *   <li>Expire after access: Evet (son erişimden 10 dk sonra expire)</li>
     * </ul>
     */
    private static final Cache<String, Object> LOOKUP_CACHE = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    /**
     * EntityManager'ı set eder (Spring injection).
     * <p>
     * <strong>ÖNEMLİ:</strong> Bu metod artık OPSİYONELDİR.
     * EntityManager ApplicationContext'ten dinamik olarak alınır.
     * Sadece performans optimizasyonu için önceden set edilebilir.
     *
     * param EntityManager Spring'in sağladığı EntityManager (proxy)
     */
    static void setEntityManager(EntityManager entityManager) {
        // Önceden set etmek istersek (opsiyonel performans optimizasyonu)
        if (entityManager != null) {
            entityManagerHolder.set(entityManager);
        }
    }

    /**
     * EntityManager'ı temizler.
     * <p>
     * Transaction sonunda çağrılmalıdır. Memory leak önler.
     */
    static void clearEntityManager() {
        entityManagerHolder.remove();
    }

    /**
     * ObjectMapper'ı set eder (Spring injection).
     * ObjectCoreInjector tarafından çağrılır.
     */
    static void setObjectMapper(ObjectMapper objectMapper) {
        ObjectCore.objectMapper = objectMapper;
    }

    /**
     * ApplicationContext'i set eder (Spring injection).
     * ObjectCoreInjector tarafından çağrılır.
     * Event publish etmek için kullanılır.
     */
    static void setApplicationContext(org.springframework.context.ApplicationContext applicationContext) {
        ObjectCore.applicationContext = applicationContext;
    }

    /**
     * JPAQueryFactory'yi set eder (Spring injection).
     * ObjectCoreInjector tarafından çağrılır.
     * Thread-safe singleton, tüm sorgularda kullanılır.
     */
    static void setQueryFactory(com.querydsl.jpa.impl.JPAQueryFactory queryFactory) {
        ObjectCore.queryFactory = queryFactory;
    }

    /**
     * EntityManager'ı döndürür (thread-local cached).
     * <p>
     * Her thread için bir kere ApplicationContext'ten EntityManager proxy'si alınır
     * ve ThreadLocal'da saklanır. Spring'in EntityManager proxy'si zaten thread-safe'dir.
     * <p>
     * Bu sayede hem @Transactional metotlarda hem de Filter'lar gibi transaction
     * scope dışında çalışan kodlarda EntityManager erişilebilir olur.
     *
     * @return Spring'in EntityManager proxy'si (thread-safe)
     * @throws IllegalStateException ApplicationContext veya EntityManager bean yoksa
     */
    static EntityManager getEntityManager() {
        // ThreadLocal'da var mı? (Cache)
        EntityManager em = entityManagerHolder.get();
        if (em != null) {
            return em;
        }

        // Yoksa ApplicationContext'ten al
        if (applicationContext == null) {
            throw new IllegalStateException(
                "ApplicationContext set edilmedi. " +
                "ObjectCoreInjector@Configuration class'ını kontrol et."
            );
        }

        try {
            // Spring'in EntityManager bean'ini al
            // Bu proxy zaten thread-safe ve transaction-aware
            em = applicationContext.getBean(EntityManager.class);
            entityManagerHolder.set(em);
            return em;
        } catch (Exception e) {
            throw new IllegalStateException(
                "EntityManager bean bulunamadı. " +
                "EntityManager configuration'unu kontrol et.",
                e
            );
        }
    }

    /**
     * ObjectMapper'ı döndürür.
     *
     * @throws IllegalStateException ObjectMapper set edilmediyse
     */
    static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            throw new IllegalStateException("ObjectMapper set edilmedi. ObjectCoreInjector@Configuration class'ını kontrol et.");
        }
        return objectMapper;
    }

    /**
     * ApplicationContext'i döndürür.
     *
     * @throws IllegalStateException ApplicationContext set edilmediyse
     */
    static org.springframework.context.ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext set edilmedi. ObjectCoreInjector@Configuration class'ını kontrol et.");
        }
        return applicationContext;
    }

    /**
     * JPAQueryFactory'yi döndürür.
     *
     * @throws IllegalStateException JPAQueryFactory set edilmediyse
     */
    static com.querydsl.jpa.impl.JPAQueryFactory getQueryFactory() {
        if (queryFactory == null) {
            throw new IllegalStateException("JPAQueryFactory set edilmedi. ObjectCoreInjector@Configuration class'ını kontrol et.");
        }
        return queryFactory;
    }

    /**
     * Tekil işlem sonucu için wrapper sınıf.
     * <p>
     * Başarılı/başarısız durumları ve hata mesajlarını taşır.
     *
     * @param <T> Sonuç veri tipi
     */
    public static class Result<T> {

        private final boolean success;
        private final T data;
        private final String error;
        private final ErrorCode errorCode;

        private Result(boolean success, T data, String error, ErrorCode errorCode) {
            this.success = success;
            this.data = data;
            this.error = error;
            this.errorCode = errorCode;
        }

        /**
         * Başarılı sonuç oluştur.
         */
        public static <T> Result<T> success(T data) {
            return new Result<>(true, data, null, null);
        }

        /**
         * Hatalı sonuç oluştur (mesaj ile).
         */
        public static <T> Result<T> error(String message) {
            return new Result<>(false, null, message, ErrorCode.UNKNOWN_ERROR);
        }

        /**
         * Hatalı sonuç oluştur (error code ile).
         */
        public static <T> Result<T> error(ErrorCode code, String message) {
            log.error(message);
            return new Result<>(false, null, message, code);
        }

        /**
         * Kaynak bulunamadı hatası oluştur.
         */
        public static <T> Result<T> notFound(String resourceName, Object id) {
            return error(ErrorCode.ENTITY_NOT_FOUND,
                String.format("%s bulunamadı: %s", resourceName, id));
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isError() {
            return !success;
        }

        public T getData() {
            if (!success) {
                throw new NoSuchElementException("Result hatalı, data yok: " + error);
            }
            return data;
        }

        public T getDataOrNull() {
            return data;
        }

        public String getError() {
            return error;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }

        /**
         * Başarılıysa işlem yap.
         */
        public Result<T> ifSuccess(Consumer<T> action) {
            if (success && data != null) {
                action.accept(data);
            }
            return this;
        }

        /**
         * Hatalıysa işlem yap.
         */
        public Result<T> ifError(Consumer<String> action) {
            if (!success) {
                action.accept(error);
            }
            return this;
        }

        /**
         * Başarılıysa dönüştür.
         */
        public <U> Result<U> map(Function<T, U> mapper) {
            if (!success) {
                return error(errorCode, error);
            }
            try {
                return success(mapper.apply(data));
            } catch (Exception e) {
                return error(ErrorCode.UNKNOWN_ERROR, e.getMessage());
            }
        }
    }

    /**
     * Liste işlem sonucu için wrapper sınıf.
     * <p>
     * Sayfalı sorgu sonuçlarını taşır.
     *
     * @param <T> Liste eleman tipi
     */
    public static class ListResult<T> {

        private final List<T> data;
        private final long total;
        private final int page;
        private final int size;
        private final boolean hasMore;

        private ListResult(List<T> data, long total, int page, int size) {
            this.data = data != null ? data : List.of();
            this.total = total;
            this.page = page;
            this.size = size;
            this.hasMore = (page + 1) * size < total;
        }

        /**
         * Sonuç oluştur.
         */
        public static <T> ListResult<T> of(List<T> data, long total, int page, int size) {
            return new ListResult<>(data, total, page, size);
        }

        /**
         * Boş sonuç oluştur.
         */
        public static <T> ListResult<T> empty() {
            return new ListResult<>(List.of(), 0, 0, 0);
        }

        /**
         * Tek sayfa (pagination yok).
         */
        public static <T> ListResult<T> of(List<T> data) {
            return new ListResult<>(data, data.size(), 0, data.size());
        }

        public List<T> getData() {
            return data;
        }

        public long getTotal() {
            return total;
        }

        public int getPage() {
            return page;
        }

        public int getSize() {
            return size;
        }

        public boolean hasMore() {
            return hasMore;
        }

        public boolean isEmpty() {
            return data.isEmpty();
        }

        public int getDataCount() {
            return data.size();
        }

        /**
         * Spring Data Page<T>'ye dönüştürür.
         * <p>
         * Service katmanından dönen ListResult'ı Spring Page formatına çevirmek için.
         * Controller'da Page<ResponseDTO> döndürmek gerektiğinde kullanışlıdır.
         * <p>
         * Kullanım:
         * <pre>
         * ObjectCore.ListResult&lt;User&gt; result = ObjectCore.list(q, predicate, pageable);
         * Page&lt;User&gt; page = result.toPage();
         * </pre>
         *
         * @return Spring Data PageImpl
         */
        public Page<T> toPage() {
            return new PageImpl<>(
                data,
                org.springframework.data.domain.PageRequest.of(page, size),
                total
            );
        }

        /**
         * Spring Data Page<T>'ye dönüştürür (map ile).
         * <p>
         * Veriyi dönüştürerek Page oluşturur (örn: Entity → DTO).
         * <p>
         * Kullanım:
         * <pre>
         * ObjectCore.ListResult&lt;User&gt; result = ObjectCore.list(q, predicate, pageable);
         * Page&lt;UserResponse&gt; page = result.toPage(user -> mapper.toResponse(user));
         * </pre>
         *
         * @param mapper Dönüştürme fonksiyonu (Function)
         * @param <R>    Dönüştürülen tip
         * @return Spring Data PageImpl (dönüştürülmüş veri ile)
         */
        public <R> Page<R> toPage(java.util.function.Function<T, R> mapper) {
            List<R> mappedData = data.stream()
                .map(mapper)
                .toList();

            return new PageImpl<>(
                mappedData,
                org.springframework.data.domain.PageRequest.of(page, size),
                total
            );
        }
    }

    // ===== AUDIT LOG YARDIMCI METOTLAR =====

    /**
     * Entity'nin loglanması gerekip gerekmediğini kontrol eder.
     *
     * @param entity Kontrol edilecek entity
     * @return true ise loglanmalı, false ise loglanmamalı
     */
    private static boolean shouldLog(Object entity) {
        try {
            // BaseEntity'de getLog() metodunu çağır
            // Entity override ederse değeri kullanılır
            if(entity instanceof BaseEntity){
                Boolean log = ((BaseEntity) entity).getLog();
                return log != null && log;
            }else{
                Boolean log = (Boolean) getFieldValue(entity, "log");
                return log != null && log;
            }

        } catch (Exception e) {
            // Hata durumunda default true, ama log yaz
            log.trace("shouldLog kontrol hatası, default true dönülüyor: entity={}, error={}",
                entity.getClass().getSimpleName(), e.getMessage());
            return true;
        }
    }

    /**
     * Audit event'i publish eder.
     * <p>
     * Asenkron olarak AuditEventListener tarafından handle edilir.
     *
     * @param entity Loglanacak entity
     * @param action İşlem tipi (CREATE, UPDATE, DELETE)
     */
    @TimeTest(ms=1000)
    private static void publishAuditEvent(Object entity, AuditAction action) {
        if (applicationContext == null) {
            return;  // ApplicationContext hazır değilse loglama
        }

        // Entity loglanmamalı ise event publish ETME!
        if (!shouldLog(entity)) {
            return;
        }

        // Entity bilgilerini önceden al (log için)
        String entityClass = entity.getClass().getName();
        String entityId = String.valueOf(getFieldValue(entity, "id"));

        try {
            // Entity'yi circular reference'sız şekilde serialize et
            // Sadece basit field'ları al, relationships'ları atla
            byte[] entityData = serializeForAudit(entity);

            // Event oluştur ve publish et
            AuditEvent event = new AuditEvent(
                    getCurrentUserId(),
                    getCurrentUsername(),
                    entityClass,
                    entityId,
                    action,
                    entityData,
                    getCurrentIp(),
                    getCurrentUserAgent(),
                    LocalDateTime.now()
            );

            applicationContext.publishEvent(event);

        } catch (Exception e) {
            log.error("publishAuditEvent kritik hata: entityClass={}, action={}, error={}",
                entityClass, action, e.getMessage(), e);
            // Log hatası işlemi engellemesin, sessizce yut
        }
    }

    /**
     * Entity'yi audit log için serialize eder.
     * <p>
     * Circular reference'ı önlemek için sadece basit field'ları alır.
     * Collection'lar ve relationships'ları atlar.
     *
     * @param entity Serialize edilecek entity
     * @return JSON byte array
     */
    private static byte[] serializeForAudit(Object entity) {
        try {
            // Entity'yi Map'e çevir (sadece basit field'larla)
            Map<String, Object> flatMap = new java.util.LinkedHashMap<>();

            // BaseEntity field'ları
            flatMap.put("id", getFieldValue(entity, "id"));
            flatMap.put("createdAt", getFieldValue(entity, "createdAt"));
            flatMap.put("updatedAt", getFieldValue(entity, "updatedAt"));
            flatMap.put("visible", getFieldValue(entity, "visible"));

            // Diğer basit field'ları yansıt
            java.lang.reflect.Field[] fields = entity.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                // Transient ve static field'ları atla
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                    java.lang.reflect.Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                // Collection ve Map field'ları atla (circular reference önleme)
                Class<?> fieldType = field.getType();
                if (java.util.Collection.class.isAssignableFrom(fieldType) ||
                    java.util.Map.class.isAssignableFrom(fieldType)) {
                    continue;
                }

                // Field'ı oku
                field.setAccessible(true);
                Object value = field.get(entity);

                // Null ve basit tipleri ekle
                if (value != null && isSimpleFieldType(value.getClass())) {
                    flatMap.put(field.getName(), value);
                }
            }

            return getObjectMapper().writeValueAsBytes(flatMap);

        } catch (Exception e) {
            log.debug("serializeForAudit hatası: entity={}, error={}",
                entity != null ? entity.getClass().getSimpleName() : "null", e.getMessage());
            // Hata durumunda boş JSON dön
            return new byte[0];
        }
    }

    /**
     * Basit field tipi kontrolü.
     * <p>
     * String, Number, Boolean, UUID, LocalDateTime, LocalDate, LocalTime, Enum
     * <p>
     * O(1) lookup performansı için Set kullanılır.
     *
     * @param clazz Kontrol edilecek class
     * @return Basit tipse true
     */
    private static boolean isSimpleFieldType(Class<?> clazz) {
        // Set'te var mı? (O(1) lookup)
        if (SIMPLE_TYPES.contains(clazz)) {
            return true;
        }

        // Number subclass'ları (BigInteger, BigDecimal vb.)
        if (Number.class.isAssignableFrom(clazz)) {
            return true;
        }

        // Enum'lar
        if (clazz.isEnum()) {
            return true;
        }

        return false;
    }

    /**
     * Giriş yapmış kullanıcının ID'sini döndürür.
     *
     * @return Kullanıcı ID'si (String), giriş yapmamışsa null
     */
    @TimeTest(ms=1000)
    private static String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                // Principal'dan ID'yi almaya çalış
                Object principal = authentication.getPrincipal();
                if (principal instanceof UUID uuid) {
                    return uuid.toString();
                }
                // Veya custom user objesi
                Field idField = principal.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                Object id = idField.get(principal);
                if (id != null) {
                    return id.toString();
                }
            }
        } catch (Exception e) {
            log.trace("getCurrentUserId hatası: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Giriş yapmış kullanıcının username'ini döndürür.
     *
     * @return Username, giriş yapmamışsa null
     */
    @TimeTest(ms=1000)
    private static String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.trace("getCurrentUsername hatası: {}", e.getMessage());
        }
        return null;
    }

    /**
     * İstemci IP adresini döndürür.
     *
     * @return IP adresi, yoksa null
     */
    @TimeTest(ms=1000)
    private static String getCurrentIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xfHeader = request.getHeader("X-Forwarded-For");
                if (xfHeader != null && !xfHeader.isEmpty()) {
                    return xfHeader.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.trace("getCurrentIp hatası: {}", e.getMessage());
        }
        return null;
    }

    /**
     * User-Agent header'ını döndürür.
     *
     * @return User-Agent, yoksa null
     */
    @TimeTest(ms=1000)
    private static String getCurrentUserAgent() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.trace("getCurrentUserAgent hatası: {}", e.getMessage());
        }
        return null;
    }

    // ===== TEMEL CRUD =====

    /**
     * Entity kaydet veya güncelle.
     * <p>
     * ID null ise INSERT, dolu ise UPDATE.
     * <p>
     * Kullanım:
     * <pre>
     * User user = new User();
     * user.setName("Ahmet");
     * Result&lt;User&gt; result = ObjectCore.save(user);
     * </pre>
     *
     * @param entity Kaydedilecek entity
     * @param <T>    Entity tipi (BaseEntity extend etmeli)
     * @return Başarılı ise saved entity, hatalı ise error message
     */
    @SuppressWarnings("unchecked")
    @TimeTest(ms=1000)
    public static <T extends BaseEntity> Result<T> save(T entity) {
        if (entity == null) {
            return Result.error(ErrorCode.VALIDATION_ERROR, "Entity null olamaz");
        }

        try {
            EntityManager em = getEntityManager();

            if (entity.getId() == null) {
                // INSERT
                em.persist(entity);
                em.flush();

                // Audit log
                if (shouldLog(entity)) {
                    publishAuditEvent(entity, AuditAction.CREATE);
                }
                invalidateLookupCache(entity.getClass(), entity.getId());
                return Result.success(entity);
            } else {
                // UPDATE
                T merged = (T) em.merge(entity);
                em.flush();

                // Audit log
                if (shouldLog(entity)) {
                    publishAuditEvent(merged, AuditAction.UPDATE);
                }
                invalidateLookupCache(entity.getClass(), entity.getId());
                return Result.success(merged);
            }
        } catch (Exception e) {
            return Result.error(ErrorCode.DATABASE_ERROR,
                "Kaydetme hatası: " + e.getMessage());
        }
    }

    /**
     * Toplu entity kaydet/güncelle (Batch insert/update).
     * <p>
     * Performans optimizasyonu:
     * <ul>
     *   <li>Her flushCount kayıtta bir veritabanına flush eder</li>
     *   <li>Bellek yönetimi için EntityManager clear yapar</li>
     *   <li>Audit log opsiyoneldir (büyük data için performans)</li>
     *   <li>Hata alan entity'leri atlar, diğerlerini kaydetmeye devam eder</li>
     * </ul>
     * <p>
     * Kullanım:
     * <pre>
     * List&lt;User&gt; users = Arrays.asList(user1, user2, user3);
     * ListResult&lt;User&gt; result = ObjectCore.saveAll(users, 50, true);
     * </pre>
     *
     * @param entities    Kaydedilecek entity listesi
     * @param flushCount  Her N kayıtta bir flush (örn: 50)
     * @param withAudit   Audit log yapılıp yapılmayacağı
     * @param <T>         Entity tipi (BaseEntity extend etmeli)
     * @return Başarılı kaydedilenler listesi
     */
    @SuppressWarnings("unchecked")
    @TimeTest(ms=2000)
    public static <T extends BaseEntity> ListResult<T> saveAll(List<T> entities, int flushCount, boolean withAudit) {
        if (entities == null || entities.isEmpty()) {
            return ListResult.empty();
        }

        if (flushCount <= 0) {
            throw new IllegalArgumentException("flushCount 0'dan büyük olmalı");
        }

        List<T> saved = new ArrayList<>();
        EntityManager em = getEntityManager();
        int errorCount = 0;

        try {
            int count = 0;

            for (T entity : entities) {
                if (entity == null) {
                    errorCount++;
                    count++;
                    continue;
                }

                try {
                    T result;
                    boolean isNew = entity.getId() == null;
                    if (entity.getId() == null) {
                        // INSERT
                        em.persist(entity);
                        result = entity;
                    } else {
                        // UPDATE
                        result = (T) em.merge(entity);
                    }

                    saved.add(result);

                    // Audit log (opsiyonel)
                    if (withAudit && shouldLog(result)) {
                        publishAuditEvent(result, isNew ? AuditAction.CREATE : AuditAction.UPDATE);
                    }

                    // Flush ve clear bellek yönetimi için
                    if (++count % flushCount == 0) {
                        em.flush();
                        em.clear();
                    }

                } catch (Exception e) {
                    errorCount++;
                    count++;
                    log.warn("saveAll hata (index {}): {}", count - 1, e.getMessage());
                }
            }

            // Kalanları flush et
            em.flush();
            em.clear();

            if (errorCount > 0) {
                log.warn("saveAll tamamlandı: {} başarılı, {} hatalı", saved.size(), errorCount);
            }

            return ListResult.of(saved, saved.size(), 0, saved.size());

        } catch (Exception e) {
            log.error("saveAll kritik hata: {}", e.getMessage());
            return ListResult.empty();
        }
    }

    /**
     * Toplu entity kaydet/güncelle (Audit log ile).
     * <p>
     * Audit log açık, flushCount = 50 varsayılan.
     *
     * @param entities Kaydedilecek entity listesi
     * @param <T>       Entity tipi
     * @return Başarılı kaydedilenler listesi
     */
    public static <T extends BaseEntity> ListResult<T> saveAll(List<T> entities) {
        return saveAll(entities, 50, true);
    }

    /**
     * Toplu entity kaydet/güncelle (Audit log olmadan).
     * <p>
     * Büyük data import için performanslı.
     *
     * @param entities   Kaydedilecek entity listesi
     * @param flushCount Her N kayıtta bir flush
     * @param <T>         Entity tipi
     * @return Başarılı kaydedilenler listesi
     */
    public static <T extends BaseEntity> ListResult<T> saveAllNoAudit(List<T> entities, int flushCount) {
        return saveAll(entities, flushCount, false);
    }

    /**
     * Soft delete - visible = false yapar.
     * <p>
     * Veritabanından silmez, sadece visible flag'ini değiştirir.
     * <p>
     * Kullanım:
     * <pre>
     * Result&lt;Void&gt; result = ObjectCore.delete(user);
     * </pre>
     *
     * @param entity Silinecek entity
     * @param <T>    Entity tipi
     * @return Başarılı ise empty Result, hatalı ise error
     */
    @SuppressWarnings("unchecked")
    @TimeTest(ms=1000)
    public static <T extends BaseEntity> Result<Void> delete(T entity) {
        if (entity == null) {
            return Result.error(ErrorCode.VALIDATION_ERROR, "Entity null olamaz");
        }

        if (entity.getId() == null) {
            return Result.error(ErrorCode.VALIDATION_ERROR, "Entity ID'si null olamaz (kayıtlı değil)");
        }

        try {
            // Audit log (silinmeden önce)
            if (shouldLog(entity)) {
                publishAuditEvent(entity, AuditAction.DELETE);
            }

            // Soft delete
            entity.setVisible(false);
            getEntityManager().merge(entity);
            getEntityManager().flush();

            return Result.success(null);
        } catch (Exception e) {
            return Result.error(ErrorCode.DATABASE_ERROR,
                "Silme hatası: " + e.getMessage());
        }
    }

    /**
     * Hard delete - entity'yi veritabanından kalıcı olarak siler.
     * <p>
     * DİKKAT: Bu işlem geri alınamaz. Veritabanından tamamen siler.
     * <p>
     * Kullanım:
     * <pre>
     * Result&lt;Void&gt; result = ObjectCore.hardDelete(user);
     * </pre>
     *
     * @param entity Silinecek entity
     * @param <T>    Entity tipi
     * @return Başarılı ise empty Result, hatalı ise error
     */
    @SuppressWarnings("unchecked")
    @TimeTest(ms=1000)
    public static <T extends BaseEntity> Result<Void> hardDelete(T entity) {
        if (entity == null) {
            return Result.error(ErrorCode.VALIDATION_ERROR, "Entity null olamaz");
        }
        if (entity.getId() == null) {
            return Result.error(ErrorCode.VALIDATION_ERROR, "Entity ID'si null olamaz");
        }
        try {
            EntityManager em = getEntityManager();
            em.remove(em.contains(entity) ? entity : em.merge(entity));
            em.flush();
            return Result.success(null);
        } catch (Exception e) {
            return Result.error(ErrorCode.DATABASE_ERROR, "Silme hatası: " + e.getMessage());
        }
    }

    /**
     * ID'ye göre entity getirir.
     * <p>
     * Soft delete kontrolü yapar (visible = false olanları getirmez).
     * <p>
     * Kullanım:
     * <pre>
     * Result&lt;User&gt; result = ObjectCore.getById(User.class, userId);
     * if (result.isSuccess()) {
     *     User user = result.getData();
     * }
     * </pre>
     *
     * @param cls Entity class'ı
     * @param id  Entity ID'si
     * @param <T> Entity tipi (BaseEntity extend etmeli)
     * @return Başarılı ise entity, hatalı ise error
     */
    @TimeTest(ms=1000)
    public static <T extends BaseEntity> Result<T> getById(Class<T> cls, UUID id) {
        if (cls == null) {
            return Result.error(ErrorCode.VALIDATION_ERROR, "Entity class null olamaz");
        }
        if (id == null) {
            return Result.error(ErrorCode.VALIDATION_ERROR, "ID null olamaz");
        }
        try {
            // PathBuilder ile type-safe dynamic path oluştur
            PathBuilder<T> pathBuilder = new PathBuilder<>(cls, cls.getSimpleName());

            // id ve visible koşulları
            Predicate predicate = pathBuilder.get("id", UUID.class).eq(id)
                    .and(pathBuilder.getBoolean("visible").eq(true));

            // Query ile fetch
            T result = getQueryFactory().selectFrom(pathBuilder)
                    .where(predicate)
                    .fetchFirst();

            if (result == null) {
                return Result.notFound(cls.getSimpleName(), id);
            }

            return Result.success(result);

        } catch (Exception e) {
            return Result.error(ErrorCode.DATABASE_ERROR, "Sorgu hatası: " + e.getMessage());
        }
    }

    /**
     * ID'ye göre entity bulur (cached).
     * <p>
     * Küçük ve sık değişmeyen lookup tablolar için optimize edilmiştir.
     * Şehir, kategori, ülke, parametre gibi tablolar için idealdir.
     * <p>
     * Cache ayarları:
     * <ul>
     *   <li>Maximum 1000 entity</li>
     *   <li>10 dakika TTL</li>
     *   <li>Expire after write (son yazmadan 10 dk sonra expire)</li>
     * </ul>
     * <p>
     * Kullanım:
     * <pre>
     * // Şehir bul - önbellekten
     * Result&lt;City&gt; city = ObjectCore.getByIdCached(City.class, cityId);
     * <p>
     * // Kategori bul - önbellekten
     * Result&lt;Category&gt; category = ObjectCore.getByIdCached(Category.class, categoryId);
     * </pre>
     *
     * @param cls Entity class'ı (BaseEntity extend etmeli)
     * @param id Entity ID'si
     * @param <T> Entity tipi
     * @return Cache'te varsa cache'ten, yoksa veritabanından
     */
    @SuppressWarnings("unchecked")
    @TimeTest(ms=1000)
    public static <T extends BaseEntity> Result<T> getByIdCached(Class<T> cls, UUID id) {
        if (cls == null) {
            return Result.error(ErrorCode.VALIDATION_ERROR, "Entity class null olamaz");
        }
        if (id == null) {
            return Result.error(ErrorCode.VALIDATION_ERROR, "ID null olamaz");
        }

        // Cache key oluştur: "ClassName:UUID"
        String cacheKey = cls.getSimpleName() + ":" + id.toString();

        // Cache'te var mı?
        Object cached = LOOKUP_CACHE.getIfPresent(cacheKey);
        if (cached != null) {
            log.trace("Cache hit: {} ({})", cls.getSimpleName(), id);
            return Result.success((T) cached);
        }

        // Cache'te yok, veritabanından al
        log.trace("Cache miss: {} ({})", cls.getSimpleName(), id);
        Result<T> result = getById(cls, id);

        // Başarılı ise cache'e ekle
        if (result.isSuccess()) {
            LOOKUP_CACHE.put(cacheKey, result.getData());
        }

        return result;
    }

    /**
     * Lookup cache'ini temizler.
     * <p>
     * Tüm cached entity'leri siler.
     * Veri güncellemesinden sonra çağrılabilir.
     * <p>
     * Kullanım:
     * <pre>
     * // Şehirler güncellendi, cache'i temizle
     * ObjectCore.clearLookupCache();
     * </pre>
     */
    public static void clearLookupCache() {
        LOOKUP_CACHE.invalidateAll();
        log.info("Lookup cache temizlendi");
    }

    /**
     * Belirli bir entity'yi cache'ten siler.
     * <p>
     * Güncelleme/delete sonrasında o entity'nin cache'ini temizler.
     * <p>
     * Kullanım:
     * <pre>
     * // Şehir güncellendi, sadece onu sil
     * ObjectCore.invalidateLookupCache(City.class, cityId);
     * </pre>
     *
     * @param cls Entity class'ı
     * @param id Entity ID'si
     */
    public static void invalidateLookupCache(Class<?> cls, UUID id) {
        String cacheKey = cls.getSimpleName() + ":" + id.toString();
        LOOKUP_CACHE.invalidate(cacheKey);
        log.trace("Cache invalidate: {} ({})", cls.getSimpleName(), id);
    }

    // ===== SORGULAMA =====

    /**
     * Belirli bir field değerine göre entity bulur.
     * <p>
     * visible = true koşulu otomatik eklenir (soft delete filtresi).
     * <p>
     * Kullanım:
     * <pre>
     * Result&lt;User&gt; result = ObjectCore.getByField(User.class, "email", "test@example.com");
     * if (result.isSuccess()) {
     *     User user = result.getData();
     * }
     * </pre>
     *
     * @param cls       Entity class'ı (BaseEntity extend etmeli)
     * @param fieldName Field adı (örn: "email", "name")
     * @param value     Aranan değer
     * @param <T>       Entity tipi
     * @return Başarılı ise entity, bulunamazsa notFound error
     */
    @TimeTest(ms=1000)
    public static <T extends BaseEntity> Result<T> getByField(Class<T> cls, String fieldName, Object value) {
        if (cls == null) {
            return Result.error(ErrorCode.VALIDATION_ERROR, "Entity class null olamaz");
        }
        if (fieldName == null || fieldName.isBlank()) {
            return Result.error(ErrorCode.VALIDATION_ERROR, "Field name null/boş olamaz");
        }
        try {
            // PathBuilder ile type-safe dynamic path oluştur
            PathBuilder<T> pathBuilder = new PathBuilder<>(cls, cls.getSimpleName());

            // visible = true koşulu (her zaman eklenir)
            com.querydsl.core.types.dsl.BooleanExpression visiblePredicate =
                    pathBuilder.getBoolean("visible").eq(true);

            // Value tipine göre predicate oluştur ve visible ile birleştir
            Predicate predicate;
            if (value instanceof String str) {
                predicate = pathBuilder.getString(fieldName).eq(str).and(visiblePredicate);
            } else if (value instanceof UUID uuid) {
                predicate = pathBuilder.get(fieldName, UUID.class).eq(uuid).and(visiblePredicate);
            } else if (value instanceof Integer num) {
                predicate = pathBuilder.getNumber(fieldName, Integer.class).eq(num).and(visiblePredicate);
            } else if (value instanceof Long num) {
                predicate = pathBuilder.getNumber(fieldName, Long.class).eq(num).and(visiblePredicate);
            } else if (value instanceof Boolean bool) {
                predicate = pathBuilder.getBoolean(fieldName).eq(bool).and(visiblePredicate);
            } else {
                // Generic Object için (enum, custom type vb.)
                predicate = pathBuilder.get(fieldName).eq(value).and(visiblePredicate);
            }

            // Query ile fetch
            T result = getQueryFactory().selectFrom(pathBuilder)
                    .where(predicate)
                    .fetchFirst();

            if (result == null) {
                return Result.notFound(cls.getSimpleName(), fieldName + "=" + value);
            }

            return Result.success(result);

        } catch (Exception e) {
            return Result.error(ErrorCode.DATABASE_ERROR, "Sorgu hatası: " + e.getMessage());
        }
    }

    // ===== QUERYDSL FIND ONE =====

    /**
     * QueryDSL ile tek kayıt bulur.
     * <p>
     * Predicate ile eşleşen İLK kaydı döndürür.
     * <p>
     * Kullanım:
     * <pre>
     * QUser q = QUser.user;
     * Predicate predicate = q.email.eq("test@example.com")
     *     .and(q.visible.isTrue());
     * Result&lt;User&gt; result = ObjectCore.findOne(q, predicate);
     *
     * if (result.isSuccess()) {
     *     User user = result.getData();
     * }
     * </pre>
     *
     * @param qEntity   QueryDSL entity path (Q-class instance)
     * @param predicate Filtre koşulları
     * @param <T>       Entity tipi
     * @return Result - Bulunamazsa notFound error
     */
    @TimeTest(ms=1000)
    public static <T> Result<T> findOne(EntityPathBase<T> qEntity, Predicate predicate) {
        if (qEntity == null) {
            return Result.error(ErrorCode.VALIDATION_ERROR, "QEntity null olamaz");
        }
        if (predicate == null) {
            return Result.error(ErrorCode.VALIDATION_ERROR, "Predicate null olamaz");
        }

        try {
            T result = getQueryFactory().selectFrom(qEntity)
                    .where(predicate)
                    .fetchFirst();

            if (result == null) {
                return Result.notFound(qEntity.getType().getSimpleName(), "sorgu kriteri");
            }

            return Result.success(result);
        } catch (Exception e) {
            return Result.error(ErrorCode.DATABASE_ERROR, "Sorgu hatası: " + e.getMessage());
        }
    }

    // ===== QUERYDSL LIST =====

    /**
     * QueryDSL ile sayfalı liste sorgusu.
     * <p>
     * Predicate ile filtreleme yapar, pagination destekler.
     * <p>
     * Kullanım:
     * <pre>
     * QUser qUser = QUser.user;
     * Predicate predicate = qUser.visible.isTrue()
     *     .and(qUser.status.eq(StatusEnum.ACTIVE));
     * Pageable pageable = PageRequest.of(0, 10);
     * ListResult&lt;User&gt; result = ObjectCore.list(qUser, predicate, pageable);
     * </pre>
     *
     * @param qEntity   QueryDSL entity path (Q-class instance)
     * @param predicate Filtre koşulları
     * @param pageable  Sayfalama bilgisi (null ise tüm sonuç)
     * @param <T>       Entity tipi
     * @return ListResult with data, total, page, size
     */
    @TimeTest(ms=3000)
    public static <T> ListResult<T> list(EntityPathBase<T> qEntity,
                                        Predicate predicate,
                                        Pageable pageable) {
        if (qEntity == null) {
            throw new IllegalArgumentException("QueryDSL entity null olamaz");
        }
        try {
            //  long total = queryFactory.selectFrom(qEntity).where(predicate).fetchCount(); Deprecate Olacak bu kullanım...
            long total = getQueryFactory().select(qEntity.count()).from(qEntity).where(predicate).fetchOne();
            List<T> data = getQueryFactory().selectFrom(qEntity).where(predicate)
                .offset(pageable != null ? pageable.getOffset() : 0)
                .limit(pageable != null ? pageable.getPageSize() : Math.min((int) total, Integer.MAX_VALUE))
                .fetch();
            int page = pageable != null ? pageable.getPageNumber() : 0;
            int size = pageable != null ? pageable.getPageSize() : Math.min((int) total, Integer.MAX_VALUE);
            return ListResult.of(data, total, page, size);
        } catch (Exception e) {
            log.error("list sorgu hatası: entity={}, error={}",
                qEntity.getType().getSimpleName(), e.getMessage());
            return ListResult.empty();
        }
    }

    /**
     * QueryDSL ile liste sorgusu (pagination olmadan).
     * <p>
     * Tüm sonuçları döndürür.
     * <p>
     * Kullanım:
     * <pre>
     * QUser qUser = QUser.user;
     * Predicate predicate = qUser.visible.isTrue();
     * ListResult&lt;User&gt; result = ObjectCore.list(qUser, predicate);
     * </pre>
     *
     * @param qEntity   QueryDSL entity path (Q-class instance)
     * @param predicate Filtre koşulları
     * @param <T>       Entity tipi
     * @return ListResult with all data
     */
    public static <T> ListResult<T> list(EntityPathBase<T> qEntity, Predicate predicate) {
        return list(qEntity, predicate, null);
    }

    /**
     * Tüm entity'leri sayfalı olarak listeler.
     * <p>
     * Sadece visible = true olan kayıtları getirir (soft delete filtreli).
     * <p>
     * Kullanım:
     * <pre>
     * ListResult&lt;User&gt; result = ObjectCore.listAll(User.class, PageRequest.of(0, 10));
     * </pre>
     *
     * @param cls      Entity sınıfı
     * @param pageable Sayfalama bilgisi (null olabilir)
     * @param <T>      Entity tipi (BaseEntity extend etmeli)
     * @return Sayfalı sonuç (data, total, page, size)
     */
    @TimeTest(ms=3000)
    public static <T extends BaseEntity> ListResult<T> listAll(Class<T> cls, Pageable pageable) {
        if (cls == null) {
            throw new IllegalArgumentException("Entity class null olamaz");
        }
        try {
            // PathBuilder ile type-safe dynamic path oluştur
            PathBuilder<T> pathBuilder = new PathBuilder<>(cls, cls.getSimpleName());

            // visible = true predicate
            Predicate predicate = pathBuilder.getBoolean("visible").eq(true);

            // Count sorgusu
            long total = getQueryFactory().select(pathBuilder.count())
                    .from(pathBuilder)
                    .where(predicate)
                    .fetchOne();

            // Data sorgusu
            List<T> data = getQueryFactory().selectFrom(pathBuilder)
                    .where(predicate)
                    .offset(pageable != null ? pageable.getOffset() : 0)
                    .limit(pageable != null ? pageable.getPageSize() :Math.min((int) total, Integer.MAX_VALUE))
                    .fetch();

            int page = pageable != null ? pageable.getPageNumber() : 0;
            int size = pageable != null ? pageable.getPageSize() : Math.min((int) total, Integer.MAX_VALUE);

            return ListResult.of(data, total, page, size);

        } catch (Exception e) {
            log.error("listAll sorgu hatası: entity={}, error={}",
                cls.getSimpleName(), e.getMessage());
            return ListResult.empty();
        }
    }

    // ===== COUNT / EXISTS =====

    /**
     * Kayıt sayısını döndürür.
     * <p>
     * QueryDSL ile filtreleme yaparak kayıt sayısını hesaplar.
     * <p>
     * Kullanım:
     * <pre>
     * QUser qUser = QUser.user;
     * Predicate predicate = qUser.status.eq(StatusEnum.ACTIVE);
     * long count = ObjectCore.count(qUser, predicate);
     * </pre>
     *
     * @param qEntity   QueryDSL entity path
     * @param predicate QueryDSL predicate
     * @param <T>       Entity tipi
     * @return Kayıt sayısı
     */
    @TimeTest(ms=1000)
    public static <T> long count(EntityPathBase<T> qEntity, Predicate predicate) {
        if (qEntity == null) {
            throw new IllegalArgumentException("qEntity null olamaz");
        }

        Long count = getQueryFactory()
                .select(qEntity.count())
                .from(qEntity)
                .where(predicate)
                .fetchOne();
        return count != null ? count : 0;
    }

    /**
     * Kayıt var mı kontrolü yapar.
     * <p>
     * QueryDSL ile filtreleme yaparak kayıt varlığını kontrol eder.
     * <p>
     * Kullanım:
     * <pre>
     * QUser qUser = QUser.user;
     * Predicate predicate = qUser.email.eq("test@example.com");
     * boolean exists = ObjectCore.exists(qUser, predicate);
     * </pre>
     *
     * @param qEntity   QueryDSL entity path
     * @param predicate QueryDSL predicate
     * @param <T>       Entity tipi
     * @return true varsa, false yoksa
     */
    public static <T> boolean exists(EntityPathBase<T> qEntity, Predicate predicate) {
        return count(qEntity, predicate) > 0;
    }

    /**
     * ID'ye göre kayıt var mı kontrolü yapar.
     * <p>
     * visible = true koşulu otomatik eklenir (soft delete filtresi).
     * <p>
     * Kullanım:
     * <pre>
     * boolean exists = ObjectCore.existsById(User.class, userId);
     * </pre>
     *
     * @param cls Entity class
     * @param id  Kayıt ID'si
     * @param <T> Entity tipi
     * @return true varsa, false yoksa
     */
    @TimeTest(ms=1000)
    public static <T extends BaseEntity> boolean existsById(Class<T> cls, UUID id) {
        if (cls == null) {
            throw new IllegalArgumentException("Entity class null olamaz");
        }
        if (id == null) {
            throw new IllegalArgumentException("ID null olamaz");
        }

        // PathBuilder ile QueryDSL sorgusu
        PathBuilder<T> pathBuilder = new PathBuilder<>(cls, cls.getSimpleName());
        Predicate predicate = pathBuilder.get("id", UUID.class).eq(id)
                .and(pathBuilder.getBoolean("visible").eq(true));

        Long count = getQueryFactory().select(pathBuilder.count())
                .from(pathBuilder)
                .where(predicate)
                .fetchOne();

        return count != null && count > 0;
    }

    // ===== NATIVE SQL =====

    /**
     * Native SQL sorgusu çalıştırır (SELECT).
     * <p>
     * QueryDSL ile yapılamayan karmaşık sorgular için:
     * <ul>
     *   <li>Database-specific fonksiyonlar</li>
     *   <li>Karmaşık JOIN'lar</li>
     *   <li>JSON operasyonları</li>
     *   <li>Full-text search</li>
     *   <li>Window functions</li>
     * </ul>
     * <p>
     * Sonuçlar Map listesi olarak döner (column name → value).
     * <p>
     * Kullanım:
     * <pre>
     * String sql = "SELECT id, name, email FROM users WHERE status = ? AND created_at > ?";
     * List&lt;Map&lt;String, Object&gt;&gt; result = ObjectCore.nativeQuery(sql, "ACTIVE", LocalDateTime.now().minusDays(30));
     * </pre>
     *
     * @param sql Native SQL sorgusu
     * @param params Parameter değerleri (sıralı)
     * @return Sonuç listesi (column → value mapping)
     */
    @TimeTest(ms=1000)
    public static List<Map<String, Object>> nativeQuery(String sql, Object... params) {
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("SQL null olamaz");
        }

        try {
            EntityManager em = getEntityManager();
            jakarta.persistence.Query query = em.createNativeQuery(sql);

            // Parameter binding
            for (int i = 0; i < params.length; i++) {
                query.setParameter(i + 1, params[i]);
            }

            @SuppressWarnings("unchecked")
            List<Object[]> rawResult = query.getResultList();

            // Column mapping (index → column name bulmak için)
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object[] row : rawResult) {
                Map<String, Object> rowMap = new java.util.LinkedHashMap<>();
                for (int i = 0; i < row.length; i++) {
                    rowMap.put("col_" + i, row[i]);
                }
                result.add(rowMap);
            }

            return result;

        } catch (Exception e) {
            log.error("Native query hatası: sql={}, error={}", sql, e.getMessage());
            throw new RuntimeException("Native query hatası: " + e.getMessage(), e);
        }
    }

    /**
     * Native SQL sorgusu çalıştırır (Entity mapping ile).
     * <p>
     * Sonuçları doğrudan Entity'ye map'ler.
     * <p>
     * Kullanım:
     * <pre>
     * String sql = "SELECT * FROM users WHERE status = ? LIMIT 10";
     * List&lt;User&gt; users = ObjectCore.nativeQuery(sql, User.class, "ACTIVE");
     * </pre>
     *
     * @param sql         Native SQL sorgusu
     * @param resultClass Mapping edilecek entity class
     * @param params      Parameter değerleri (sıralı)
     * @param <T>         Entity tipi
     * @return Entity listesi
     */
    @SuppressWarnings("unchecked")
    @TimeTest(ms=1000)
    public static <T extends BaseEntity> List<T> nativeQuery(String sql, Class<T> resultClass, Object... params) {
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("SQL null olamaz");
        }
        if (resultClass == null) {
            throw new IllegalArgumentException("ResultClass null olamaz");
        }

        try {
            EntityManager em = getEntityManager();
            jakarta.persistence.Query query = em.createNativeQuery(sql, resultClass);

            // Parameter binding
            for (int i = 0; i < params.length; i++) {
                query.setParameter(i + 1, params[i]);
            }

            List<T> result = query.getResultList();

            // Soft delete filtre ( BaseEntity ise)
            if (BaseEntity.class.isAssignableFrom(resultClass)) {
                result = result.stream()
                    .filter(e -> ((BaseEntity) e).getVisible())
                    .toList();
            }

            log.debug("Native query tamamlandı: entity={}, count={}",
                resultClass.getSimpleName(), result.size());

            return result;

        } catch (Exception e) {
            log.error("Native query hatası: sql={}, entity={}, error={}",
                sql, resultClass.getSimpleName(), e.getMessage());
            throw new RuntimeException("Native query hatası: " + e.getMessage(), e);
        }
    }

    /**
     * Native SQL ile INSERT/UPDATE/DELETE çalıştırır.
     * <p>
     * Bulk insert, batch update, delete işlemleri için.
     * <p>
     * Kullanım:
     * <pre>
     * // Bulk insert
     * String sql = "INSERT INTO temp_users (id, name, email) VALUES (?, ?, ?)";
     * int inserted = ObjectCore.nativeUpdate(sql, uuid, "Ali", "ali@example.com");
     * <p>
     * // Bulk update
     * String sql = "UPDATE users SET status = ? WHERE created_at < ?";
     * int updated = ObjectCore.nativeUpdate(sql, "INACTIVE", LocalDateTime.now().minusMonths(6));
     * </pre>
     *
     * @param sql    Native SQL (INSERT/UPDATE/DELETE)
     * @param params Parameter değerleri (sıralı)
     * @return Etkilen satır sayısı
     */
    @TimeTest(ms=1000)
    public static int nativeUpdate(String sql, Object... params) {
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("SQL null olamaz");
        }

        try {
            EntityManager em = getEntityManager();
            jakarta.persistence.Query query = em.createNativeQuery(sql);

            // Parameter binding
            for (int i = 0; i < params.length; i++) {
                query.setParameter(i + 1, params[i]);
            }

            int affectedRows = query.executeUpdate();

            log.debug("Native update tamamlandı: sql={}, affected={}", sql, affectedRows);

            return affectedRows;

        } catch (Exception e) {
            log.error("Native update hatası: sql={}, error={}", sql, e.getMessage());
            throw new RuntimeException("Native update hatası: " + e.getMessage(), e);
        }
    }

    /**
     * Native SQL ile single value (scalar) sorgusu.
     * <p>
     * COUNT, SUM, AVG, MAX, MIN gibi aggregate sorgular için.
     * <p>
     * Kullanım:
     * <pre>
     * // Count
     * String sql = "SELECT COUNT(*) FROM users WHERE status = ?";
     * Long count = ObjectCore.nativeQueryScalar(sql, Long.class, "ACTIVE");
     * <p>
     * // Sum
     * String sql = "SELECT SUM(amount) FROM orders WHERE status = ?";
     * Double total = ObjectCore.nativeQueryScalar(sql, Double.class, "COMPLETED");
     * <p>
     * // Single value
     * String sql = "SELECT name FROM users WHERE id = ?";
     * String name = ObjectCore.nativeQueryScalar(sql, String.class, userId);
     * </pre>
     *
     * @param sql         Native SQL sorgusu (tek sütun dönmeli)
     * @param resultClass Dönen tip (Long.class, String.class, vb.)
     * @param params      Parameter değerleri (sıralı)
     * @param <T>         Sonuç tipi
     * @return Sorgu sonucu (null dönebilir)
     */
    @SuppressWarnings("unchecked")
    @TimeTest(ms=1000)
    public static <T> T nativeQueryScalar(String sql, Class<T> resultClass, Object... params) {
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("SQL null olamaz");
        }
        if (resultClass == null) {
            throw new IllegalArgumentException("ResultClass null olamaz");
        }

        try {
            EntityManager em = getEntityManager();
            jakarta.persistence.Query query = em.createNativeQuery(sql);

            // Parameter binding
            for (int i = 0; i < params.length; i++) {
                query.setParameter(i + 1, params[i]);
            }

            Object result = query.getSingleResult();

            return (T) result;

        } catch (jakarta.persistence.NoResultException e) {
            log.trace("Native scalar query: sonuc yok (NoResultException)");
            return null;
        } catch (Exception e) {
            log.error("Native scalar query hatası: sql={}, error={}", sql, e.getMessage());
            throw new RuntimeException("Native scalar query hatası: " + e.getMessage(), e);
        }
    }

    // ===== REFLECTION FIELD METOTLARI =====

    /**
     * Entity'nin field değerini okur.
     * <p>
     * Class ve parent class'larda field arar.
     * <p>
     * Kullanım:
     * <pre>
     * Object name = ObjectCore.getFieldValue(user, "name");
     * </pre>
     *
     * @param entity    Entity
     * @param fieldName Field adı
     * @return Field değeri
     * @throws ReflectionException Hata durumunda
     */
    @TimeTest(ms=100)
    public static Object getFieldValue(Object entity, String fieldName) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity null olamaz");
        }
        if (fieldName == null || fieldName.isEmpty()) {
            throw new IllegalArgumentException("Field name boş olamaz");
        }

        try {
            Field field = findField(entity.getClass(), fieldName);
            field.setAccessible(true);
            return field.get(entity);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ReflectionException(
                ErrorCode.REFLECTION_ERROR,
                "Field okunamadı: " + fieldName + ", message: " + e.getMessage()
            );
        }
    }

    /**
     * Entity'nin field değerini set eder.
     * <p>
     * Class ve parent class'larda field arar.
     * <p>
     * Kullanım:
     * <pre>
     * ObjectCore.setFieldValue(user, "name", "Ahmet");
     * </pre>
     *
     * @param entity    Entity
     * @param fieldName Field adı
     * @param value     Yeni değer
     * @throws ReflectionException Hata durumunda
     */
    @TimeTest(ms=100)
    public static void setFieldValue(Object entity, String fieldName, Object value) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity null olamaz");
        }
        if (fieldName == null || fieldName.isEmpty()) {
            throw new IllegalArgumentException("Field name boş olamaz");
        }

        try {
            Field field = findField(entity.getClass(), fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ReflectionException(
                ErrorCode.REFLECTION_ERROR,
                "Field set edilemedi: " + fieldName + ", message: " + e.getMessage()
            );
        }
    }

    /**
     * Source objedeki property'leri target objeye kopyalar.
     * <p>
     * Aynı isimli ve tipte field'lar kopyalanır.
     * Static, transient ve synthetic field'lar atlanır.
     * <p>
     * Kullanım:
     * <pre>
     * UserDTO source = new UserDTO();
     * source.setName("Ahmet");
     * source.setEmail("ahmet@test.com");
     *
     * User target = new User();
     * ObjectCore.copyProperties(source, target);
     * // target.name = "Ahmet", target.email = "ahmet@test.com"
     * </pre>
     *
     * @param source Kaynak obje
     * @param target Hedef obje
     * @param <S>    Source tipi
     * @param <T>    Target tipi
     * @return Target obje (method chaining için)
     * @throws IllegalArgumentException Source veya target null ise
     */
    @TimeTest(ms=100)
    public static <S, T> T copyProperties(S source, T target) {
        if (source == null) {
            throw new IllegalArgumentException("Source null olamaz");
        }
        if (target == null) {
            throw new IllegalArgumentException("Target null olamaz");
        }

        Class<?> sourceClass = source.getClass();
        Class<?> targetClass = target.getClass();

        // Source'daki tüm field'ları al
        Field[] sourceFields = sourceClass.getDeclaredFields();

        for (Field sourceField : sourceFields) {
            String fieldName = sourceField.getName();

            // Static, transient, ve synthetic field'ları atla
            if (Modifier.isStatic(sourceField.getModifiers()) ||
                Modifier.isTransient(sourceField.getModifiers()) ||
                sourceField.isSynthetic()) {
                continue;
            }

            try {
                // Target'da aynı isimde field var mı?
                Field targetField = findField(targetClass, fieldName);

                // Tip kontrolü
                if (!targetField.getType().equals(sourceField.getType())) {
                    continue;
                }

                sourceField.setAccessible(true);
                targetField.setAccessible(true);

                Object value = sourceField.get(source);
                targetField.set(target, value);

            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // Field yoksa veya erişilemezse atla
            }
        }

        return target;
    }

    /**
     * Class ve parent class'larda field arar.
     *
     * @param clazz     Class
     * @param fieldName Field adı
     * @return Field
     * @throws NoSuchFieldException Bulunamazsa
     */
    @TimeTest(ms=1000)
    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field bulunamadı: " + fieldName);
    }

    /**
     * ReflectionException için RuntimeException.
     * <p>
     * Reflection işlemlerinde hata durumunda fırlatılır.
     */
    public static class ReflectionException extends RuntimeException {

        private final ErrorCode errorCode;

        /**
         * Yeni bir reflection exception oluşturur.
         *
         * @param errorCode Hata kodu
         * @param message   Hata mesajı
         */
        public ReflectionException(ErrorCode errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        /**
         * Hata kodunu döndürür.
         *
         * @return ErrorCode
         */
        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }

    // ===== JSON SERILESTIRME =====

    /**
     * JSON serileştirme/deserileştirme hatası.
     * <p>
     * ObjectCore serialize/deserialize metodlarında fırlatılır.
     */
    public static class SerializationException extends RuntimeException {

        private final ErrorCode errorCode;

        public SerializationException(ErrorCode errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public SerializationException(ErrorCode errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }

    /**
     * Objeyi JSON string'e çevirir.
     * <p>
     * Kullanım:
     * <pre>
     * String json = ObjectCore.serialize(user);
     * </pre>
     *
     * @param obj Obje
     * @return JSON string
     * @throws SerializationException Hata durumunda
     */
    @TimeTest(ms=100)
    public static String serialize(Object obj) {
        if (obj == null) {
            return null;
        }

        try {
            return getObjectMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new SerializationException(
                ErrorCode.SERIALIZATION_ERROR,
                "JSON serileştirme hatası: " + e.getMessage()
            );
        }
    }

    /**
     * JSON string'i obje'ye çevirir.
     * <p>
     * Kullanım:
     * <pre>
     * User user = ObjectCore.deserialize(json, User.class);
     * </pre>
     *
     * @param json JSON string
     * @param cls  Hedef class
     * @param <T>  Obje tipi
     * @return Obje
     * @throws SerializationException Hata durumunda
     */
    @TimeTest(ms=100)
    public static <T> T deserialize(String json, Class<T> cls) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        if (cls == null) {
            throw new IllegalArgumentException("Hedef class null olamaz");
        }

        try {
            return getObjectMapper().readValue(json, cls);
        } catch (JsonProcessingException e) {
            throw new SerializationException(
                ErrorCode.SERIALIZATION_ERROR,
                "JSON deserileştirme hatası: " + e.getMessage()
            );
        }
    }

    /**
     * JSON string'i obje'ye çevirir (TypeReference ile).
     * <p>
     * Generic type'lar için (List, Map vb.)
     * <p>
     * Kullanım:
     * <pre>
     * List&lt;User&gt; users = ObjectCore.deserialize(json, new TypeReference&lt;List&lt;User&gt;&gt;() {});
     * </pre>
     *
     * @param json           JSON string
     * @param typeReference TypeReference
     * @param <T>            Obje tipi
     * @return Obje
     * @throws SerializationException Hata durumunda
     */
    @TimeTest(ms=100)
    public static <T> T deserialize(String json, TypeReference<T> typeReference) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        if (typeReference == null) {
            throw new IllegalArgumentException("TypeReference null olamaz");
        }

        try {
            return getObjectMapper().readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new SerializationException(
                ErrorCode.SERIALIZATION_ERROR,
                "JSON deserileştirme hatası: " + e.getMessage()
            );
        }
    }

    /**
     * Entity'nin derin kopyasını (deep copy) oluşturur.
     * <p>
     * JSON serialize/deserialize kullanarak deep copy yapar.
     * ID ve timestamp alanları temizlenir, böylece kopya yeni kayıt gibi davranır.
     * <p>
     * Kullanım:
     * <pre>
     * User original = ...;
     * User copy = ObjectCore.clone(original);
     * copy.setName("Copy");
     * ObjectCore.save(copy); // Yeni kayıt olarak kaydedilir
     * </pre>
     *
     * @param entity Kopyalanacak entity
     * @param <T>    Entity tipi (BaseEntity extend etmeli)
     * @return Yeni kopya (ID ve timestamp'lar null)
     * @throws IllegalArgumentException Entity null ise
     * @throws SerializationException   Hata durumunda
     */
    @SuppressWarnings("unchecked")
    @TimeTest(ms=100)
    public static <T extends BaseEntity> T clone(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity null olamaz");
        }

        try {
            // JSON'a çevir
            String json = serialize(entity);

            // Yeni obje oluştur (ID null olacak)
            T cloned = (T) deserialize(json, entity.getClass());

            // ID'yi temizle - yeni kayıt gibi davranacak
            cloned.setId(null);
            cloned.setCreatedAt(null);
            cloned.setUpdatedAt(null);
            cloned.setCreatedBy(null);

            return cloned;
        } catch (SerializationException e) {
            // Re-throw as-is
            throw e;
        } catch (Exception e) {
            throw new SerializationException(
                ErrorCode.SERIALIZATION_ERROR,
                "Kopyalama başarısız: " + e.getMessage()
            );
        }
    }
}
