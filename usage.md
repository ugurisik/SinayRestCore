# SinayRestCore - Kullanım Kılavuzu

> **Proje:** SinayRestCore REST API Framework
> **Version:** 2.1
> **Son Güncelleme:** 2026-03-29

---

## İçindekiler

1. [Anotasyonlar](#1-anotasyonlar)
2. [Cache Sistemi](#2-cache-sistemi)
3. [Audit Logging](#3-audit-logging)
4. [Event System](#4-event-system)
5. [TimeTester](#5-timetester)
6. [Job Scheduler](#6-job-scheduler)
7. [Notification System](#7-notification-system)
8. [Rate Limiting](#8-rate-limiting)
9. [File Upload](#9-file-upload)
10. [QueryDSL](#10-querydsl)
11. [Base Pattern'lar](#11-base-patternlar)
12. [Pagination & Sorting](#12-pagination--sorting)
13. [Request Logging](#13-request-logging)
14. [WebSocket](#14-websocket)

---

## 1. Anotasyonlar

### 1.1 @Cacheable

Metodun dönüş değerini cache'e alır.

```java
@Service
public class UserService {

    // Basit cache'leme (5 dakika TTL)
    @Cacheable(value = "users", key = "#id", ttl = 300)
    public UserResponse getById(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    // Koşullu cache'leme
    @Cacheable(value = "users", key = "#id", ttl = 600, condition = "#id != null")
    public UserResponse getByIdOrNull(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElse(null);
    }

    // Unless ile hariç tutma
    @Cacheable(value = "users", key = "#email", unless = "#result == null")
    public UserResponse getByEmail(String email) {
        return repository.findByEmail(email)
                .map(mapper::toResponse)
                .orElse(null);
    }
}
```

**Parametreler:**
| Parametre | Tip | Açıklama |
|-----------|-----|----------|
| value | String | Cache ismi (zorunlu) |
| key | String | Cache anahtarı (SpEL) |
| ttl | int | TTL süresi (saniye, varsayılan: 300) |
| condition | String | Koşul (SpEL) |
| unless | String | Hariç tutma koşulu (SpEL) |

**SpEL Örnekleri:**
```java
@Cacheable(value = "users", key = "#id")              // #p0 parametresi
@Cacheable(value = "users", key = "#user.id")          // #user parametresinin id'si
@Cacheable(value = "users", key = "#p0 + ':' + #p1")   // İki parametrenin birleşimi
@Cacheable(value = "users", key = "#root.methodName")  // Metod adı
```

---

### 1.2 @CacheEvict

Cache'ten değer siler.

```java
@Service
public class UserService {

    // Tek anahtarı sil
    @CacheEvict(value = "users", key = "#id")
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    // Tüm cache'i temizle
    @CacheEvict(value = "users", allEntries = true)
    public void clearAllUsersCache() {
        // Tüm "users" cache'i temizlenir
    }

    // Metottan önce sil (update senaryoları için)
    @CacheEvict(value = "users", key = "#id", beforeInvocation = true)
    public void update(UUID id, UpdateRequest request) {
        // Update öncesi cache temizlenir, stale data önlenir
        User user = repository.findById(id).orElseThrow();
        mapper.update(user, request);
        repository.save(user);
    }

    // Koşullu silme
    @CacheEvict(value = "users", key = "#result.id", condition = "#result != null")
    public UserResponse update(UUID id, UpdateRequest request) {
        // Sadece sonuç null değilse sil
    }
}
```

**Parametreler:**
| Parametre | Tip | Açıklama |
|-----------|-----|----------|
| value | String | Cache ismi (zorunlu) |
| key | String | Silinecek anahtar (SpEL) |
| allEntries | boolean | Tüm cache'i temizle |
| beforeInvocation | boolean | Metottan önce sil |
| condition | String | Koşul (SpEL) |

---

### 1.3 @AuditLog

Metod çalıştırıldığında audit log kaydı oluşturur.

```java
@Service
public class UserService {

    // Basit audit log
    @AuditLog(action = AuditAction.CREATE, entityType = "USER")
    public UserResponse create(CreateRequest request) {
        // Log otomatik oluşturulur
    }

    // Entity ID ile
    @AuditLog(action = AuditAction.DELETE, entityType = "USER", entityId = "#id")
    public void delete(UUID id) {
        // id parametresi log'a eklenir
    }

    // Özel mesaj ile
    @AuditLog(
        action = AuditAction.UPDATE,
        entityType = "USER",
        message = "Kullanıcı profilini güncelledi"
    )
    public UserResponse updateProfile(UUID id, UpdateProfileRequest request) {
        // Mesaj log'a eklenir
    }

    // Sadece işlem tipi (diğerleri otomatik)
    @AuditLog(action = AuditAction.LOGIN)
    public void login(LoginRequest request) {
        // Kullanıcı, IP, User Agent otomatik eklenir
    }
}
```

**Parametreler:**
| Parametre | Tip | Açıklama |
|-----------|-----|----------|
| action | AuditAction | İşlem tipi (LOGIN, CREATE, UPDATE, DELETE, vb.) |
| entityType | String | Entity tipi ("USER", "FILE", "ORDER") |
| entityId | String | Entity ID'si (SpEL) |
| includeUserId | boolean | Kullanıcı ID loglansın (varsayılan: true) |
| includeIpAddress | boolean | IP adresi loglansın (varsayılan: true) |
| includeUserAgent | boolean | User Agent loglansın (varsayılan: true) |
| message | String | Ek mesaj |

**AuditAction Seçenekleri:**
```java
LOGIN, LOGOUT, REGISTER,
CREATE, UPDATE, DELETE, READ,
BAN, UNBAN, FORGIVE,
FILE_UPLOAD, FILE_DELETE,
ADMIN_ACTION, OTHER
```

---

### 1.4 @PublishEvent

Metod çalıştırıldıktan sonra event yayınlar.

```java
@Service
public class UserService {

    // Basit event yayınlama
    @PublishEvent(eventType = UserRegisteredEvent.class)
    public UserResponse register(RegisterRequest request) {
        User user = createUser(request);
        // Metot tamamlandıktan sonra event otomatik yayınlanır
        return mapper.toResponse(user);
    }

    // Sonucu payload'a ekle
    @PublishEvent(
        eventType = UserCreatedEvent.class,
        includeResult = true
    )
    public UserResponse create(CreateRequest request) {
        // #result payload'a eklenir
    }

    // Parametreleri payload'a ekle
    @PublishEvent(
        eventType = PasswordChangedEvent.class,
        includeArguments = true
    )
    public void changePassword(UUID userId, String newPassword) {
        // #userId, #newPassword payload'a eklenir
    }

    // Hata durumunda da yayınla
    @PublishEvent(
        eventType = LoginAttemptEvent.class,
        publishOnError = true
    )
    public void login(LoginRequest request) {
        // Başarısız olsa bile event yayınlanır
        // Error bilgisi payload'a eklenir
    }
}
```

**Event Listener Tanımlama:**
```java
@Component
public class NotificationEventListener {

    @EventListener
    public void handleUserRegistered(UserRegisteredEvent event) {
        String email = event.get("email");
        UUID userId = event.get("userId", UUID.class);

        // Hoş geldin email'i gönder
        sendWelcomeEmail(email);
    }

    @EventListener
    public void handleUserBanned(UserBannedEvent event) {
        // Admin bildirimi gönder
        notifyAdmins("Kullanıcı banlandı: " + event.get("userId"));
    }
}
```

**Parametreler:**
| Parametre | Tip | Açıklama |
|-----------|-----|----------|
| eventType | Class<? extends DomainEvent> | Event tipi |
| eventBuilder | String | Builder metodu adı |
| async | boolean | Asenkron yayınla (varsayılan: true) |
| publishOnError | boolean | Hata durumunda da yayınla |
| includeArguments | boolean | Parametreleri payload'a ekle |
| includeResult | boolean | Sonucu payload'a ekle |

---

### 1.5 @TimeTest

Metod çalışma süresini test eder.

```java
@Service
public class ProductService {

    // 30ms limit
    @TimeTest(ms = 30)
    public List<Product> getAllProducts() {
        return repository.findAll();
        // 30ms'den uzun sürerse WARN log yazılır
    }

    // 100ms limit, ERROR seviyesi
    @TimeTest(ms = 100, level = TimeTest.LogLevel.ERROR)
    public List<Order> getUserOrders(UUID userId) {
        return orderRepository.findByUserId(userId);
    }

    // Custom mesaj ile
    @TimeTest(ms = 50, message = "Yavaş sorgu: {method} {actual}ms sürdü!")
    public List<Product> searchProducts(String keyword) {
        return repository.search(keyword);
    }

    // Exception fırlat (test için)
    @TimeTest(ms = 10, throwOnFailure = true)
    public void fastOperation() {
        // 10ms'den uzun sürerse exception fırlatır
        // Production için önerilmez!
    }
}
```

**Parametreler:**
| Parametre | Tip | Açıklama |
|-----------|-----|----------|
| ms | long | Maksimum süre (milisaniye) |
| level | LogLevel | Log seviyesi (TRACE, DEBUG, INFO, WARN, ERROR) |
| message | String | Custom mesaj formatı |
| throwOnFailure | boolean | Exception fırlat (varsayılan: false) |

**Log Çıktısı:**
```
WARN  c.s.core.timetest.TimeTestAspect - [TIME TEST FAILED] getAllProducts took 45ms (limit: 30ms) at com.example.ProductService.getAllProducts(ProductService.java:42)
```

---

### 1.6 @ScheduledJob

Zamanlanmış iş (scheduled job) tanımlar.

```java
@Service
public class CleanupService {

    // Cron ile (her gece 02:00'de)
    @ScheduledJob(
        name = "cleanupOldLogs",
        cron = "0 0 2 * * ?",
        useLock = true,
        lockTimeoutMinutes = 60
    )
    public void cleanupOldAuditLogs() {
        // 90 günden eski logları temizle
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        auditService.cleanOldLogs(cutoff);
    }

    // Fixed rate ile (her 5 dakikada bir)
    @ScheduledJob(
        name = "syncData",
        fixedRate = 300000, // 5 dakika (ms)
        useLock = true
    )
    public void syncExternalData() {
        // Dış sistem ile veri senkronizasyonu
    }

    // Fixed delay ile (bir önceki tamamlandıktan 10 dakika sonra)
    @ScheduledJob(
        name = "generateReports",
        fixedDelay = 600000, // 10 dakika (ms)
        initialDelay = 30000  // Başlangıç gecikme 30 saniye
    )
    public void generateDailyReports() {
        // Günlük raporları oluştur
    }

    // Cluster ortamı için distributed lock
    @ScheduledJob(
        name = "processPayments",
        cron = "0 */10 * * * ?", // Her 10 dakikada bir
        useLock = true,
        lockTimeoutMinutes = 15,
        description = "Bekleyen ödemeleri işler"
    )
    public void processPendingPayments() {
        // Sadece bir instance çalıştırır
    }
}
```

**Cron Expression Formatı:**
```
Format: saniye dakika saat gün-ay gün-hafta

┌───────────── saniye (0-59)
│ ┌───────────── dakika (0-59)
│ │ ┌───────────── saat (0-23)
│ │ │ ┌───────────── gün (1-31)
│ │ │ │ ┌───────────── ay (1-12)
│ │ │ │ │ ┌───────────── hafta (0-7, 0 ve 7 = Pazar)
│ │ │ │ │ │
* * * * * *

Örnekler:
"0 0 * * * *"     -> Her saat başı
"0 0 2 * * *"     -> Her gece 02:00'de
"0 0 2 * * MON"   -> Her Pazartesi 02:00'de
"0 */5 * * * *"   -> Her 5 dakikada bir
"0 0 12 1 * *"    -> Her ayın 1'i 12:00'de
"0 0 0 * * *"     -> Her gece yarısı
```

**Parametreler:**
| Parametre | Tip | Açıklama |
|-----------|-----|----------|
| name | String | Job adı (benzersiz) |
| cron | String | Cron expression |
| fixedRate | long | Sabit oran (ms) |
| fixedDelay | long | Sabit gecikme (ms) |
| initialDelay | long | İlk gecikme (ms) |
| useLock | boolean | Distributed lock kullan |
| lockTimeoutMinutes | int | Lock timeout (dakika) |
| description | String | Job açıklaması |

---

### 1.7 @RateLimit

İstek sınırlaması uygular.

```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    // Global rate limit yeterli (100 req/dakika)
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        // Global limit uygulanır
    }

    // Kritik endpoint - özel limit (5 req/dakika)
    @PostMapping("/register")
    @RateLimit(capacity = 5, refillTokens = 5, refillDuration = 1, banThreshold = 3)
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        // 5 istek/dakika, 3 strike = 10 dakika ban
    }

    // Çok sıkı limit (2 req/5 dakika)
    @PostMapping("/forgot-password")
    @RateLimit(capacity = 2, refillTokens = 2, refillDuration = 5, banThreshold = 2)
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        // 2 istek/5 dakika, 2 strike = ban
    }
}
```

**Parametreler:**
| Parametre | Tip | Açıklama |
|-----------|-----|----------|
| capacity | int | Kova kapasitesi (maksimum istek) |
| refillTokens | int | Dolum token sayısı |
| refillDuration | int | Dolum süresi (dakika) |
| banThreshold | int | Ban sınırı (strike sayısı) |

**Hangi Endpoint'lere Özel Limit Gerekir?**
| Endpoint | Global Yeterli? | Özel Limit |
|----------|-----------------|------------|
| GET /api/v1/products | ✅ Evet | ❌ Hayır |
| POST /api/v1/auth/register | ⚠️ Zayıf | ✅ **Evet** (5 req/dakika) |
| POST /api/v1/auth/login | ⚠️ Zayıf | ✅ **Evet** (5 req/dakika) |
| POST /api/v1/auth/forgot-password | ⚠️ Zayıf | ✅ **Evet** (2 req/5 dakika) |

---

## 2. Cache Sistemi

### 2.1 Manuel Cache İşlemleri

CacheService ile programatik cache işlemleri:

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final CacheService cacheService;
    private final ProductRepository repository;

    // Cache'e al
    public void cacheProduct(UUID productId, ProductResponse response) {
        cacheService.put("products", productId, response, 600); // 10 dakika
    }

    // Cache'ten oku
    public ProductResponse getCachedProduct(UUID productId) {
        return cacheService.get("products", productId, ProductResponse.class);
    }

    // Cache'ten sil
    public void evictProduct(UUID productId) {
        cacheService.evict("products", productId);
    }

    // Tüm cache'i temizle
    public void evictAllProducts() {
        cacheService.evictAll("products");
    }

    // Varlık kontrolü
    public boolean isProductCached(UUID productId) {
        return cacheService.exists("products", productId);
    }

    // Multi-get
    public Map<UUID, ProductResponse> getCachedProducts(List<UUID> ids) {
        return cacheService.getAll("products", ids, ProductResponse.class);
    }

    // TTL uzat
    public void extendProductCache(UUID productId) {
        cacheService.expire("products", productId, 1200); // 20 dakika
    }
}
```

**CacheService Metodları:**
```java
// Cache'e al
cacheService.put(cacheName, key, value, ttlSeconds);
cacheService.put(cacheName, key, value); // Varsayılan TTL: 300s

// Cache'ten oku
T value = cacheService.get(cacheName, key, Class<T>);

// Çoklu okuma
Map<Object, T> values = cacheService.getAll(cacheName, keys, Class<T>);

// Cache'ten sil
cacheService.evict(cacheName, key);
cacheService.evictAll(cacheName, keys);
cacheService.evictAll(cacheName);

// Varlık kontrolü
boolean exists = cacheService.exists(cacheName, key);

// Anahtarları listele
Set<String> keys = cacheService.keys(cacheName);

// TTL işlemleri
long ttl = cacheService.getTtl(cacheName, key);
cacheService.expire(cacheName, key, ttlSeconds);
```

---

## 3. Audit Logging

### 3.1 Audit Log Sorgulama

```java
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;

    // Kullanıcıya ait log'lar
    public List<AuditLogEntity> getUserLogs(String userId) {
        return auditLogRepository.findByUserIdAndVisibleTrueOrderByExecutionTimeDesc(userId);
    }

    // İşlem tipine göre log'lar
    public List<AuditLogEntity> getActionLogs(AuditAction action) {
        return auditLogRepository.findByActionAndVisibleTrueOrderByExecutionTimeDesc(action);
    }

    // Entity'ye ait log'lar
    public List<AuditLogEntity> getEntityLogs(String entityType, String entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdAndVisibleTrueOrderByExecutionTimeDesc(
                entityType, entityId
        );
    }

    // Tarih aralığına göre log'lar
    public List<AuditLogEntity> getDateRangeLogs(LocalDateTime start, LocalDateTime end) {
        return auditLogRepository.findByExecutionTimeBetweenAndVisibleTrueOrderByExecutionTimeDesc(
                start, end
        );
    }
}
```

**Audit Log Temizleme (Scheduled Job):**
```java
@ScheduledJob(
    name = "cleanupOldAuditLogs",
    cron = "0 0 3 * * ?", // Her gece 03:00'de
    description = "90 günden eski audit log'ları temizler"
)
public void cleanupOldAuditLogs() {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
    int deleted = auditService.cleanOldLogs(cutoff);
    log.info("Temizlenen audit log sayısı: {}", deleted);
}
```

---

## 4. Event System

### 4.1 Custom Event Oluşturma

```java
// Event sınıfı
@Getter
@AllArgsConstructor
public class UserRegisteredEvent extends DomainEvent {

    public UserRegisteredEvent(UUID userId, String email, String username) {
        super(
            "UserRegisteredEvent",
            Map.of(
                "userId", userId,
                "email", email,
                "username", username,
                "occurredAt", LocalDateTime.now().toString()
            )
        );
    }
}

// Event yayınlama
@Service
public class UserService {

    private final EventPublisher eventPublisher;

    public UserResponse register(RegisterRequest request) {
        User user = createUser(request);

        // Manuel event yayınlama
        Map<String, Object> payload = Map.of(
            "userId", user.getId(),
            "email", user.getEmail()
        );
        eventPublisher.publish("UserRegisteredEvent", payload);

        return mapper.toResponse(user);
    }
}
```

### 4.2 Event Listener

```java
@Component
@Slf4j
public class UserEventListeners {

    private final EmailService emailService;
    private final NotificationService notificationService;

    @EventListener
    @Async
    public void handleUserRegistered(UserRegisteredEvent event) {
        UUID userId = event.get("userId", UUID.class);
        String email = event.get("email", String.class);

        // Hoş geldin email'i gönder
        emailService.sendWelcomeEmail(email);

        // İç bildirim oluştur
        notificationService.createNotification(
            userId,
            NotificationType.IN_APP,
            "Hoş Geldiniz!",
            "Aramıza hoş geldiniz. İyi eğlenceler!"
        );
    }

    @EventListener
    @Async
    public void handlePasswordChanged(PasswordChangedEvent event) {
        String email = event.get("email", String.class);

        // Şifre değiştirildi bildirimi
        emailService.sendPasswordChangedEmail(email);
    }

    @EventListener
    @Async
    public void handleUserBanned(UserBannedEvent event) {
        UUID adminId = event.get("adminId", UUID.class);
        UUID bannedUserId = event.get("userId", UUID.class);
        String reason = event.get("reason", String.class);

        // Admin'e bildirim gönder
        notificationService.createNotification(
            adminId,
            NotificationType.IN_APP,
            "Kullanıcı Banlandı",
            String.format("Kullanıcı banlandı: %s. Sebep: %s", bannedUserId, reason)
        );
    }
}
```

---

## 5. TimeTester

### 5.1 Performans Test Senaryoları

```java
@Service
public class PerformanceTestService {

    // Database sorgusu testi
    @TimeTest(ms = 50)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // External API çağrısı testi
    @TimeTest(ms = 5000, level = TimeTest.LogLevel.INFO)
    public ExternalData fetchExternalData() {
        return externalApiClient.getData();
    }

    // Karmaşık işlem testi
    @TimeTest(ms = 100, message = "Rapor oluşturma çok yavaş: {actual}ms!")
    public Report generateComplexReport() {
        // Rapor oluşturma işlemleri
    }

    // Batch işlem testi
    @TimeTest(ms = 30000, level = TimeTest.LogLevel.WARN) // 30 saniye
    public void processBatch(List<Item> items) {
        for (Item item : items) {
            processItem(item);
        }
    }
}
```

---

## 6. Job Scheduler

### 6.1 Job Örnekleri

```java
@Service
public class ScheduledJobs {

    // Her gece çalışan job'lar
    @ScheduledJob(
        name = "dailyDataCleanup",
        cron = "0 0 1 * * ?", // Her gece 01:00'de
        useLock = true,
        lockTimeoutMinutes = 120,
        description = "Günlük veri temizliği"
    )
    public void dailyCleanup() {
        // Eski logları temizle
        auditService.cleanOldLogs(LocalDateTime.now().minusDays(90));

        // Eski bildirimleri temizle
        notificationService.cleanOldNotifications(180);

        // Expire olmuş job lock'larını temizle
        jobLockRepository.deactivateExpiredLocks(LocalDateTime.now());
    }

    // Her saat çalışan job
    @ScheduledJob(
        name = "hourlyMetrics",
        cron = "0 0 * * * *",
        useLock = false, // Tek instance gerekmiyor
        description = "Saatlik metrik hesaplama"
    )
    public void calculateHourlyMetrics() {
        // Son bir saatlik metrikleri hesapla
    }

    // Her 5 dakikada bir çalışan job
    @ScheduledJob(
        name = "processPendingNotifications",
        fixedRate = 300000, // 5 dakika
        useLock = true,
        lockTimeoutMinutes = 10,
        description = "Bekleyen bildirimleri gönder"
    )
    public void processNotifications() {
        notificationService.sendPendingNotifications();
        notificationService.retryFailedNotifications();
    }

    // Her saat başı çalışan sync job
    @ScheduledJob(
        name = "syncExternalData",
        cron = "0 0 * * * *",
        useLock = true,
        lockTimeoutMinutes = 60,
        description = "Dış sistem veri senkronizasyonu"
    )
    public void syncExternalData() {
        // Dış sistemlerden veri çek
    }

    // Her Pazar gece çalışan rapor job
    @ScheduledJob(
        name = "weeklyReport",
        cron = "0 0 2 * * SUN", // Her Pazar 02:00'de
        useLock = true,
        lockTimeoutMinutes = 180,
        description = "Haftalık rapor oluşturma"
    )
    public void generateWeeklyReports() {
        // Haftalık raporları oluştur ve email olarak gönder
    }
}
```

### 6.2 Job Lock Servisi Kullanımı

```java
@Service
public class ManualJobService {

    private final JobLockService jobLockService;

    public void runManualJob(String jobName) {
        // Lock almaya çalış
        boolean acquired = jobLockService.tryAcquireLock(jobName, 30, true);

        if (!acquired) {
            log.warn("Job zaten başka instance tarafından çalıştırılıyor: {}", jobName);
            return;
        }

        try {
            // Job'u çalıştır
            performJobTask(jobName);
        } finally {
            // Lock'ı serbest bırak
            jobLockService.releaseLock(jobName);
        }
    }

    // Job lock bilgisi sorgula
    public JobLockInfo getJobStatus(String jobName) {
        Optional<JobLockEntity> lock = jobLockService.getLockInfo(jobName);

        if (lock.isEmpty()) {
            return JobLockInfo.notFound();
        }

        JobLockEntity jobLock = lock.get();
        return JobLockInfo.builder()
                .jobName(jobLock.getJobName())
                .instanceId(jobLock.getInstanceId())
                .lockedAt(jobLock.getLockedAt())
                .isActive(jobLock.getIsActive())
                .lastExecutionTime(jobLock.getLastExecutionTime())
                .lastExecutionSuccess(jobLock.getLastExecutionSuccess())
                .build();
    }
}
```

---

## 7. Notification System

### 7.1 Bildirim Gönderme

```java
@Service
@RequiredArgsConstructor
public class UserNotificationService {

    private final NotificationService notificationService;

    // Basit bildirim
    public void sendWelcomeNotification(UUID userId) {
        notificationService.createNotification(
            userId,
            NotificationType.EMAIL,
            "Hoş Geldiniz!",
            "Aramıza hoş geldiniz. Umarız güzel zaman geçirirsiniz."
        );
    }

    // Zamanlanmış bildirim
    public void scheduleReminderNotification(UUID userId, LocalDateTime reminderTime) {
        notificationService.createScheduledNotification(
            userId,
            NotificationType.IN_APP,
            "Hatırlatma",
            "Etkinlik yaklaşıyor!",
            reminderTime
        );
    }

    // Payload ile bildirim
    public void sendCustomEmail(UUID userId, String subject, String content) {
        String payload = String.format(
            "{\"email\": \"%s\", \"template\": \"custom\", \"data\": {...}}",
            getUserEmail(userId)
        );

        notificationService.createNotification(
            userId,
            NotificationType.EMAIL,
            subject,
            content,
            payload
        );
    }

    // Okunmamış bildirimleri getir
    public List<Notification> getUnreadNotifications(UUID userId) {
        return notificationService.getUnreadNotifications(userId);
    }

    // Okunmamış sayısı
    public long getUnreadCount(UUID userId) {
        return notificationService.getUnreadCount(userId);
    }

    // Okundu olarak işaretle
    public void markAsRead(UUID notificationId) {
        notificationService.markAsRead(notificationId);
    }

    // Tümünü okundu olarak işaretle
    public void markAllAsRead(UUID userId) {
        notificationService.markAllAsRead(userId);
    }
}
```

### 7.2 Notification Channel Implementasyonu

```java
@Component
@RequiredArgsConstructor
public class SmsChannel implements NotificationChannel {

    private final SmsGateway smsGateway;

    @Override
    public void send(Notification notification) throws Exception {
        String phoneNumber = extractPhoneNumber(notification);

        smsGateway.sendSms(
            phoneNumber,
            notification.getMessage()
        );

        log.info("SMS sent: to={}", phoneNumber);
    }

    @Override
    public boolean supports(NotificationType type) {
        return NotificationType.SMS.equals(type);
    }

    private String extractPhoneNumber(Notification notification) {
        // Payload'dan telefon numarasını al
        if (notification.getPayload() != null) {
            // JSON parse et
            return parsePhoneFromJson(notification.getPayload());
        }
        return null;
    }
}

@Component
@RequiredArgsConstructor
public class PushChannel implements NotificationChannel {

    private final FcmClient fcmClient;

    @Override
    public void send(Notification notification) throws Exception {
        String fcmToken = getUserFcmToken(notification.getUserId());

        fcmClient.send(
            fcmToken,
            notification.getTitle(),
            notification.getMessage(),
            parsePayload(notification.getPayload())
        );
    }

    @Override
    public boolean supports(NotificationType type) {
        return NotificationType.PUSH.equals(type);
    }
}
```

### 7.3 Scheduled Job ile Bildirim Gönderimi

```java
@Service
public class NotificationScheduler {

    private final NotificationService notificationService;

    @ScheduledJob(
        name = "sendPendingNotifications",
        cron = "0 */5 * * * *", // Her 5 dakikada bir
        useLock = true
    )
    public void sendPending() {
        notificationService.sendPendingNotifications();
    }

    @ScheduledJob(
        name = "retryFailedNotifications",
        cron = "0 */10 * * * *", // Her 10 dakikada bir
        useLock = true
    )
    public void retryFailed() {
        notificationService.retryFailedNotifications();
    }

    @ScheduledJob(
        name = "cleanupOldNotifications",
        cron = "0 0 4 * * *", // Her gece 04:00'de
        useLock = true
    )
    public void cleanupOld() {
        notificationService.cleanOldNotifications(180); // 180 gün
    }
}
```

---

## 8. Rate Limiting

### 8.1 Rate Limit Yapılandırması

```yaml
# application.yaml
rate-limit:
  global:
    capacity: 100           # 100 istek/dakika (tüm endpoint'ler için)
    refill-tokens: 100
    ban-threshold: 15       # 15 strike = 10 dakika ban
    key-type: IP_AND_USER   # IP ve kullanıcıya göre
```

### 8.2 Ban Yönetimi

```java
@Service
@RequiredArgsConstructor
public class BanManagementService {

    private final BanCacheService banCacheService;

    public void unbanUser(String ipOrUserId) {
        banCacheService.removeBan(ipOrUserId);
        log.info("Ban kaldırıldı: {}", ipOrUserId);
    }

    public boolean isBanned(String ipOrUserId) {
        return banCacheService.isBanned(ipOrUserId);
    }

    public List<RateLimitBan> getActiveBans() {
        return banCacheService.getActiveBans();
    }
}
```

---

## 9. File Upload

### 9.1 Dosya Yükleme

```java
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController extends BaseController<FileService, FileRequest, FileResponse, FileQueryDto> {

    private final FileService fileService;

    @PostMapping("/upload")
    @RateLimit(capacity = 10, refillTokens = 10, refillDuration = 1, banThreshold = 5)
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "IMAGE") FileCategory category
    ) {
        FileUploadResponse response = fileService.uploadFile(file, category);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable UUID fileId) {
        FileResponse file = fileService.getById(fileId);
        Resource resource = fileService.loadFileAsResource(file.getStoragePath());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getOriginalFilename() + "\"")
                .body(resource);
    }
}
```

---

## 10. QueryDSL

### 10.1 Type-Safe Query

```java
@Repository
@RequiredArgsConstructor
public class UserQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final QUser qUser = QUser.user;

    public Page<User> search(String keyword, UserStatus status, Pageable pageable) {
        List<Predicate> predicates = new ArrayList<>();

        // Keyword search
        if (StringUtils.hasText(keyword)) {
            predicates.add(
                qUser.username.containsIgnoreCase(keyword)
                    .or(qUser.email.containsIgnoreCase(keyword))
            );
        }

        // Status filter
        if (status != null) {
            predicates.add(qUser.status.eq(status));
        }

        // Always filter visible
        predicates.add(qUser.visible.isTrue());

        // Execute
        List<User> results = queryFactory
                .selectFrom(qUser)
                .where(BooleanExpression.allOf(predicates.toArray(new Predicate[0])))
                .orderBy(qUser.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // Count
        long total = queryFactory
                .select(qUser.count())
                .from(qUser)
                .where(BooleanExpression.allOf(predicates.toArray(new Predicate[0])))
                .fetchOne();

        return new PageImpl<>(results, pageable, total);
    }
}
```

---

## 11. Base Pattern'lar

### 11.1 BaseEntity

```java
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    // BaseEntity şunları sağlar:
    // - UUID id (otomatik)
    // - createdAt, updatedAt
    // - visible (soft delete)
    // - createdBy
}
```

### 11.2 BaseRepository

```java
public interface UserRepository extends JpaRepository<User, UUID>, BaseRepository {
    // BaseRepository şunları sağlar:
    // - visible = true otomatik filtre
    // - softDelete() metodu
    // - findAll(), count() vb. visible filtreli
}
```

### 11.3 BaseService

```java
@Service
public class UserServiceImpl extends BaseService<User, UUID> implements UserService {
    // BaseService şunları sağlar:
    // - generic CRUD metodları
    // - softDelete wrapper
    // - getById() visible kontrolü
}
```

---

## Hızlı Referans

### Anotasyon Hızlı Karar Ağacı

```
├── Performans testi gerekli mi?
│   └── EVET → @TimeTest(ms=X)
│
├── Cache'lenmeli mi?
│   ├── Okuma işlemi → @Cacheable
│   └── Yazma işlemi → @CacheEvict
│
├── Audit log gerekli mi?
│   └── EVET → @AuditLog
│
├── Event publish gerekli mi?
│   └── EVET → @PublishEvent
│
├── Zamanlanmış işlem mi?
│   └── EVET → @ScheduledJob
│
├── Rate limit gerekli mi?
│   └── Kritik endpoint → @RateLimit
│
├── Pagination gerekli mi?
│   └── EVET → PageableRequest
│
└── Real-time bildirim gerekli mi?
    └── EVET → WebSocket broadcast/sendToUser
```

### Cache Kullanım Kuralları

| Senaryo | Anotasyon | TTL |
|----------|-----------|-----|
| Tek nesne okuma | @Cacheable(value="users", key="#id") | 300s |
| Liste okuma | @Cacheable(value="users", key="'all'") | 60s |
| Arama sonucu | @Cacheable(value="users", key="#keyword") | 180s |
| Güncelleme sonrası | @CacheEvict(value="users", key="#id") | - |
| Toplu silme | @CacheEvict(value="users", allEntries=true) | - |

### Audit Kullanım Kuralları

| Senaryo | Action | entityType |
|----------|--------|------------|
| Kullanıcı kayıt | REGISTER | USER |
| Kullanıcı giriş | LOGIN | - |
| Kullanıcı çıkış | LOGOUT | - |
| Oluşturma | CREATE | ENTITY_TYPE |
| Güncelleme | UPDATE | ENTITY_TYPE |
| Silme | DELETE | ENTITY_TYPE |
| Dosya yükleme | FILE_UPLOAD | FILE |
| Admin işlemi | ADMIN_ACTION | - |

### Scheduled Job Kullanım Kuralları

| Sıklık | Cron Expression | Kullanım |
|--------|----------------|----------|
| Her saat | `0 0 * * * *` | Metrik hesaplama |
| Her gece | `0 0 2 * * *` | Temizlik job'ları |
| Haftalık | `0 0 2 * * MON` | Haftalık rapor |
| 5 dakikada | `0 */5 * * * *` | Sync job'ları |
| Aylık | `0 0 2 1 * *` | Aylık rapor |

### Pagination Kullanım Kuralları

| Senaryo | Query Params | Varsayılan |
|----------|-------------|-----------|
| İlk sayfa | `page=0&size=20` | page=0, size=20 |
| Sonraki sayfa | `page=1&size=20` | - |
| Sıralı | `sort=name&direction=ASC` | sort=createdAt, direction=DESC |
| Büyük sayfa | `page=0&size=100` | max size=100 |

### WebSocket Kullanım Kuralları

| Senaryo | Metot | Kullanım |
|----------|-------|----------|
| Tüm kullanıcılara bildirim | `broadcast()` | Sistem duyuruları |
| Tek kullanıcıya bildirim | `sendToUser(userId, ...)` | Kişisel bildirim |
| Sistem mesajı | `sendSystemMessage()` | Sistem bilgilendirmesi |
| Alert/Üyarı | `sendAlert()` | Önemli uyarılar |
| Veri güncelleme | `sendDataUpdate()` | Real-time update |

### Request Logging Kuralları

| Özellik | Açıklama |
|---------|----------|
| Correlation ID | Her request için benzersiz ID |
| X-Correlation-ID header | Client custom ID verebilir |
| Slow request | >1000ms WARN log |
| X-Forwarded-For | Proxy arkası IP tespiti |

---

## 12. Pagination & Sorting

### 12.1 PageableRequest Kullanımı

Standart sayfalama DTO'su ile tüm list endpoint'lerinde tutarlı pagination:

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // Query param ile pagination
    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserResponse>>> list(
            @RequestParam(required = false) String keyword,
            PageableRequest pageRequest
    ) {
        Page<UserResponse> page = userService.list(keyword, pageRequest.toPageable());
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    // DTO body ile pagination
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> search(
            @Valid @RequestBody SearchRequest request
    ) {
        Page<UserResponse> page = userService.search(
            request.getKeyword(),
            request.getPageRequest().toPageable()
        );
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
```

**PageableRequest Özellikleri:**
| Field | Tip | Varsayılan | Validation |
|-------|-----|-----------|------------|
| page | int | 0 | min: 0 |
| size | int | 20 | min: 1, max: 100 |
| sort | String | "createdAt" | - |
| direction | String | "DESC" | ASC/DESC |

### 12.2 Service Kullanımı

```java
@Service
@RequiredArgsConstructor
public class UserService {

    public Page<UserResponse> list(String keyword, Pageable pageable) {
        QUser q = QUser.user;
        List<Predicate> predicates = new ArrayList<>();

        if (StringUtils.hasText(keyword)) {
            predicates.add(
                q.name.containsIgnoreCase(keyword)
                    .or(q.email.containsIgnoreCase(keyword))
            );
        }

        // visible filtresi
        predicates.add(q.visible.isTrue());

        ObjectCore.ListResult<User> result = ObjectCore.list(
            q,
            BooleanExpression.allOf(predicates.toArray(new Predicate[0])),
            pageable
        );

        return new PageImpl<>(
            result.getData().stream()
                .map(mapper::toResponse)
                .toList(),
            pageable,
            result.getTotal()
        );
    }
}
```

### 12.3 Client-Side Kullanım

**Query Param Örnekleri:**
```bash
# İlk sayfa, varsayılan (20 kayıt)
GET /api/v1/users?page=0&size=20&sort=createdAt&direction=DESC

# İkinci sayfa, 50 kayıt
GET /api/v1/users?page=1&size=50&sort=name&direction=ASC

# İsme göre artan sıralama
GET /api/v1/users?page=0&size=20&sort=name&direction=ASC

# Email'e göre azalan sıralama
GET /api/v1/users?page=0&size=10&sort=email&direction=DESC

# Karmaşık arama ile birlikte
GET /api/v1/users?keyword=ahmet&page=0&size=20&sort=createdAt&direction=DESC
```

**JavaScript/Fetch Örneği:**
```javascript
const fetchUsers = async (page = 0, size = 20) => {
    const params = new URLSearchParams({
        page: page.toString(),
        size: size.toString(),
        sort: 'createdAt',
        direction: 'DESC'
    });

    const response = await fetch(`/api/v1/users?${params}`);
    const data = await response.json();

    return {
        content: data.data.content,
        totalPages: data.data.totalPages,
        totalElements: data.data.totalElements,
        currentPage: data.data.page,
        hasNext: !data.data.last,
        hasPrevious: !data.data.first
    };
};
```

**Axios Örneği:**
```javascript
import axios from 'axios';

const getUserList = async (keyword, page, size) => {
    const response = await axios.get('/api/v1/users', {
        params: {
            keyword,
            page,
            size,
            sort: 'createdAt',
            direction: 'DESC'
        }
    });

    return response.data.data;
};
```

### 12.4 PageRequestUtils Kullanımı

```java
// Manuel Pageable oluşturma
Pageable pageable = PageRequestUtils.toPageable(
    PageableRequest.builder()
        .page(0)
        .size(20)
        .sort("name")
        .direction("ASC")
        .build()
);

// Direction parsing
Sort.Direction direction = PageRequestUtils.parseDirection("ASC"); // ASC
Sort.Direction direction2 = PageRequestUtils.parseDirection("invalid"); // DESC (default)

// Page normalization (negatifse 0 yap)
int normalizedPage = PageRequestUtils.normalizePage(-5); // 0
int normalizedPage2 = PageRequestUtils.normalizePage(3); // 3

// Size normalization (1-100 arası)
int normalizedSize = PageRequestUtils.normalizeSize(0); // 20 (default)
int normalizedSize2 = PageRequestUtils.normalizeSize(150); // 100 (max)
```

---

## 13. Request Logging

### 13.1 Otomatik Request/Response Logging

Tüm HTTP istekleri ve yanıtları otomatik loglanır:

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    // Otomatik loglama:
    // - Request URL, method, headers
    // - Response status, duration
    // - Correlation ID tracking
    // - X-Forwarded-For handling (proxy arkası)
    // - Slow request detection (>1000ms)
}
```

**Log Çıktısı:**
```log
2026-03-29 10:30:45.123 [a1b2c3d4-e5f6-7890-abcd-ef1234567890] INFO  c.s.c.filter.RequestLoggingFilter : REQUEST: GET /api/v1/users?page=0&size=20 | IP: 192.168.1.100 | User-Agent: Mozilla/5.0...
2026-03-29 10:30:45.234 [a1b2c3d4-e5f6-7890-abcd-ef1234567890] INFO  c.s.c.filter.RequestLoggingFilter : RESPONSE: status=200 | duration=111ms
```

### 13.2 Correlation ID Tracking

Her request için benzersiz correlation ID oluşturulur:

**Request Header ile Correlation ID:**
```bash
# Client correlation ID gönderirse
curl -H "X-Correlation-ID: my-custom-id-123" http://localhost:8080/api/v1/users

# Log çıktısı:
2026-03-29 10:30:45.123 [my-custom-id-123] INFO  c.s.c.filter.RequestLoggingFilter : REQUEST: GET /api/v1/users
```

**Response Header:**
```bash
# Response'da correlation ID döner
HTTP/1.1 200 OK
X-Correlation-ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890
Content-Type: application/json
```

### 13.3 Logback Konfigürasyonu

```xml
<!-- logback-spring.xml -->
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %clr([%X{correlationId:-N/A}]){magenta} %-15.15thread %clr(%-5level){highlight} %clr(%logger{36}){cyan} - %msg%n</pattern>
```

**MDC Kullanımı (Kod İçinde):**
```java
@Service
@RequiredArgsConstructor
public class OrderService {

    public void createOrder(CreateOrderRequest request) {
        // Correlation ID'yi al
        String correlationId = MDC.get("correlationId");

        log.info("Sipariş oluşturuluyor. Correlation ID: {}", correlationId);

        // İşlem logları correlation ID ile birlikte görünür
        // [a1b2c3d4-e5f6-7890-abcd-ef1234567890] INFO  c.s.core.service.OrderService : Sipariş oluşturuluyor.
    }
}
```

### 13.4 Slow Request Detection

1000ms'den uzun süren istekler otomatik WARN seviyesinde loglanır:

```log
2026-03-29 10:35:12.345 [a1b2c3d4...] WARN  c.s.c.filter.RequestLoggingFilter : SLOW REQUEST: POST /api/v1/reports/generate | duration=1523ms | IP: 192.168.1.100
```

### 13.5 X-Forwarded-For Desteği

Proxy arkasındaki gerçek IP adresi tespiti:

```java
// Filter otomatik olarak şu sırayla kontrol eder:
// 1. X-Forwarded-For header
// 2. X-Real-IP header
// 3. Remote address (doğrudan IP)

// Log çıktısı:
// REQUEST: GET /api/v1/users | IP: 203.0.113.42 (X-Forwarded-For)
```

---

## 14. WebSocket

### 14.1 Client-Side Bağlantı

SockJS ve STOMP ile WebSocket bağlantısı:

```html
<!DOCTYPE html>
<html>
<head>
    <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
</head>
<body>
    <script>
        // JWT token (login sonrası alınır)
        const token = localStorage.getItem('jwtToken');

        // WebSocket bağlantısı
        const socket = new SockJS('http://localhost:8080/ws');
        const stompClient = Stomp.over(socket);

        // JWT token ile bağlan
        const headers = {
            'Authorization': 'Bearer ' + token
        };

        stompClient.connect(headers, function (frame) {
            console.log('WebSocket bağlantısı başarılı:', frame);

            // Broadcast mesajlarını dinle
            stompClient.subscribe('/topic/broadcast', function (message) {
                const data = JSON.parse(message.body);
                console.log('Broadcast mesajı:', data);
                showNotification(data);
            });

            // Kullanıcıya özel mesajları dinle
            stompClient.subscribe('/user/queue/messages', function (message) {
                const data = JSON.parse(message.body);
                console.log('Kişisel mesaj:', data);
                showNotification(data);
            });

        }, function (error) {
            console.error('WebSocket bağlantı hatası:', error);
        });

        function showNotification(data) {
            alert(`${data.title}: ${data.content}`);
        }
    </script>
</body>
</html>
```

### 14.2 Server-Side WebSocketService

```java
@Service
@RequiredArgsConstructor
public class MyWebSocketService {

    private final WebSocketService webSocketService;

    // Tüm kullanıcılara broadcast
    public void notifyAllUsers(String message) {
        webSocketService.broadcast(
            MessageType.NOTIFICATION,
            message,
            "Sistem Bildirimi"
        );
    }

    // Tek kullanıcıya mesaj
    public void notifyUser(UUID userId, String message) {
        webSocketService.sendToUser(
            userId,
            MessageType.NOTIFICATION,
            message,
            "Kişisel Bildirim"
        );
    }

    // Sistem mesajı
    public void sendSystemAlert(String alert) {
        webSocketService.sendSystemMessage(
            alert,
            "Sistem Uyarısı"
        );
    }

    // Alert mesajı
    public void sendAlert(String title, String content) {
        webSocketService.sendAlert(title, content);
    }

    // Data update bildirimi
    public void notifyDataUpdate(String entityType, UUID entityId) {
        webSocketService.sendDataUpdate(
            entityType,
            entityId.toString(),
            "Veri güncellendi"
        );
    }
}
```

### 14.3 REST Endpoint ile Broadcast

Admin panelinden WebSocket broadcast:

```java
@AdminRestController
@RequestMapping("/api/v1/websocket")
@RequiredArgsConstructor
public class WebSocketTestController {

    private final WebSocketService webSocketService;

    // Tüm kullanıcılara broadcast
    @PostMapping("/broadcast")
    public ResponseEntity<ApiResponse<Void>> broadcast(@Valid @RequestBody BroadcastRequest request) {
        webSocketService.broadcast(
            MessageType.NOTIFICATION,
            request.getContent(),
            request.getTitle()
        );
        return ResponseEntity.ok(ApiResponse.success());
    }

    // Tek kullanıcıya mesaj
    @PostMapping("/send/{userId}")
    public ResponseEntity<ApiResponse<Void>> sendToUser(
            @PathVariable UUID userId,
            @Valid @RequestBody MessageRequest request
    ) {
        webSocketService.sendToUser(
            userId,
            request.getType(),
            request.getContent(),
            request.getTitle()
        );
        return ResponseEntity.ok(ApiResponse.success());
    }

    // Sistem mesajı
    @PostMapping("/system")
    public ResponseEntity<ApiResponse<Void>> sendSystem(@Valid @RequestBody SystemMessageRequest request) {
        webSocketService.sendSystemMessage(request.getContent(), request.getTitle());
        return ResponseEntity.ok(ApiResponse.success());
    }

    // Alert mesajı
    @PostMapping("/alert")
    public ResponseEntity<ApiResponse<Void>> sendAlert(@Valid @RequestBody AlertRequest request) {
        webSocketService.sendAlert(request.getTitle(), request.getContent());
        return ResponseEntity.ok(ApiResponse.success());
    }

    // Data update bildirimi
    @PostMapping("/data-update")
    public ResponseEntity<ApiResponse<Void>> sendDataUpdate(@Valid @RequestBody DataUpdateRequest request) {
        webSocketService.sendDataUpdate(
            request.getEntityType(),
            request.getEntityId(),
            request.getMessage()
        );
        return ResponseEntity.ok(ApiResponse.success());
    }
}
```

### 14.4 WebSocket Message Formatı

```java
@Getter
@Builder
public class WebSocketMessage {
    private UUID messageId;        // Benzersiz mesaj ID'si
    private MessageType type;       // Mesaj tipi
    private String content;         // Mesaj içeriği
    private String title;           // Mesaj başlığı
    private UUID senderId;          // Gönderen ID'si
    private String senderName;      // Gönderen adı
    private UUID targetUserId;      // Hedef kullanıcı ID'si
    private Map<String, Object> data; // Ek veri
    private LocalDateTime timestamp; // Zaman damgası
}
```

### 14.5 MessageType Seçenekleri

```java
public enum MessageType {
    NOTIFICATION,    // Bildirim
    CHAT,           // Sohbet mesajı
    SYSTEM,         // Sistem mesajı
    ALERT,          // Uyarı
    ACTIVITY,       // Aktivite
    DATA_UPDATE,    // Veri güncelleme
    ERROR           // Hata
}
```

### 14.6 Client-Side Mesaj Gönderme

Client'tan sunucuya mesaj gönderme:

```javascript
// Sunucuya mesaj gönder
stompClient.send('/app/message', {}, JSON.stringify({
    type: 'CHAT',
    content: 'Merhaba!',
    title: 'Sohbet'
}));

// Aktivite bildirimi
stompClient.send('/app/activity', {}, JSON.stringify({
    type: 'ACTIVITY',
    content: 'Kullanıcı dashboarda giriş yaptı',
    data: {
        page: '/dashboard',
        action: 'view'
    }
}));
```

### 14.7 WebSocket Test İçin Postman/cURL

**cURL ile WebSocket Test:**
```bash
# WebSocket bağlantısı testi (wscat tool'u ile)
wscat -c "http://localhost:8080/ws" -H "Authorization: Bearer YOUR_JWT_TOKEN"

# REST endpoint ile broadcast
curl -X POST http://localhost:8080/api/v1/websocket/broadcast \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test Bildirimi",
    "content": "Bu bir test mesajıdır"
  }'
```

### 14.8 Gerçek Kullanım Senaryoları

**Senaryo 1: Yeni Sipariş Bildirimi**
```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final WebSocketService webSocketService;

    public OrderResponse create(CreateOrderRequest request) {
        Order order = createOrder(request);

        // Adminlere yeni sipariş bildirimi
        webSocketService.broadcast(
            MessageType.NOTIFICATION,
            String.format("Yeni sipariş: %s - %s TL", order.getId(), order.getTotalAmount()),
            "Yeni Sipariş"
        );

        // Müşteriye sipariş onayı
        webSocketService.sendToUser(
            order.getCustomerId(),
            MessageType.NOTIFICATION,
            "Siparişiniz alındı. Sipariş NO: " + order.getId(),
            "Sipariş Onayı"
        );

        return mapper.toResponse(order);
    }
}
```

**Senaryo 2: Real-time Veri Güncelleme**
```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final WebSocketService webSocketService;

    public ProductResponse updatePrice(UUID productId, BigDecimal newPrice) {
        Product product = repository.findById(productId).orElseThrow();
        product.setPrice(newPrice);
        repository.save(product);

        // Tüm kullanıcılara fiyat güncelleme bildirimi
        webSocketService.sendDataUpdate(
            "PRODUCT",
            productId.toString(),
            "Ürün fiyatı güncellendi"
        );

        return mapper.toResponse(product);
    }
}
```

**Senaryo 3: Kullanıcı Ban Bildirimi**
```java
@Service
@RequiredArgsConstructor
public class AdminService {

    private final WebSocketService webSocketService;

    public void banUser(UUID userId, String reason) {
        User user = userService.getById(userId);
        user.setAccountLocked(true);
        userService.save(user);

        // Banlanan kullanıcıya bildirim
        webSocketService.sendToUser(
            userId,
            MessageType.ALERT,
            "Hesabınız banlandı. Sebep: " + reason,
            "Hesap Askıya Alındı"
        );

        // Adminlere bildirim
        webSocketService.broadcast(
            MessageType.ACTIVITY,
            String.format("Kullanıcı banlandı: %s - Sebep: %s", user.getEmail(), reason),
            "Admin Bildirimi"
        );
    }
}
```

---

*Son güncelleme: 2026-03-29*
*Developer: Uğur Işık*
*Version: 2.1 - Pagination, Request Logging, WebSocket özellikleri eklendi*
