# SinayRestCore - Kullanım Kılavuzu

> **Proje:** SinayRestCore REST API Framework
> **Version:** 2.1
> **Son Güncelleme:** 2026-03-29

---

## İçindekiler

1. [ObjectCore - Temel CRUD](#1-objectcore---temel-crud)
2. [Anotasyonlar](#2-anotasyonlar)
3. [Cache Sistemi](#3-cache-sistemi)
4. [Audit Logging](#4-audit-logging)
5. [Event System](#5-event-system)
6. [TimeTester](#6-timetester)
7. [Job Scheduler](#7-job-scheduler)
8. [Notification System](#8-notification-system)
9. [Rate Limiting](#9-rate-limiting)
10. [File Upload](#10-file-upload)
11. [QueryDSL](#11-querydsl)
12. [Pagination & Sorting](#12-pagination--sorting)
13. [Request Logging](#13-request-logging)
14. [WebSocket](#14-websocket)

---

## 1. ObjectCore - Temel CRUD

### 1.1 Nedir?

**ObjectCore**, repository yazmadan hızlı CRUD işlemleri yapan statik utility class'tır.

**Avantajları:**
- ✅ Repository yazmana gerek yok
- ✅ Bean injection gerekmez
- ✅ QueryDSL entegrasyonlu
- ✅ Soft delete desteği (visible kontrolü otomatik)
- ✅ Result wrapper ile kolay hata yönetimi

### 1.2 Save - Kaydet/Güncelle

```java
@Service
public class UserService {

    // Yeni kayıt
    public UserResponse create(CreateRequest request) {
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());

        Result<User> result = ObjectCore.save(user);

        if (result.isSuccess()) {
            User saved = result.getData();
            return mapper.toResponse(saved);
        } else {
            throw new BadRequestException(result.getError());
        }
    }

    // Builder ile
    public UserResponse create(CreateRequest request) {
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .build();

        Result<User> result = ObjectCore.save(user);

        if (!result.isSuccess()) {
            throw new BadRequestException(result.getError());
        }

        return mapper.toResponse(result.getData());
    }
}
```

### 1.3 GetById - ID ile Getir

```java
public UserResponse getById(UUID id) {
    Result<User> result = ObjectCore.getById(User.class, id);

    if (result.isSuccess()) {
        return mapper.toResponse(result.getData());
    } else {
        throw new ResourceNotFoundException("User", id);
    }
}

// Cache ile
public UserResponse getByIdCached(UUID id) {
    Result<User> result = ObjectCore.getByIdCached(User.class, id);

    if (result.isSuccess()) {
        return mapper.toResponse(result.getData());
    } else {
        throw new ResourceNotFoundException("User", id);
    }
}
```

### 1.4 GetByField - Field Değeri ile

```java
public UserResponse getByEmail(String email) {
    Result<User> result = ObjectCore.getByField(User.class, "email", email);

    if (result.isSuccess()) {
        return mapper.toResponse(result.getData());
    } else {
        throw new ResourceNotFoundException("User", "email", email);
    }
}
```

### 1.5 List - Liste Getir

```java
// Tüm listesi (sayfalı)
public Page<UserResponse> list(Pageable pageable) {
    ObjectCore.ListResult<User> result = ObjectCore.listAll(User.class, pageable);

    return new PageImpl<>(
        result.getData().stream().map(mapper::toResponse).toList(),
        pageable,
        result.getTotal()
    );
}

// QueryDSL ile filtreli
public Page<UserResponse> search(String keyword, Pageable pageable) {
    QUser q = QUser.user;
    Predicate predicate = q.name.containsIgnoreCase(keyword).and(q.visible.isTrue());

    ObjectCore.ListResult<User> result = ObjectCore.list(q, predicate, pageable);

    return new PageImpl<>(
        result.getData().stream().map(mapper::toResponse).toList(),
        pageable,
        result.getTotal()
    );
}

// toPage() kullanımı
public Page<UserResponse> list(String keyword, Pageable pageable) {
    QUser q = QUser.user;
    Predicate predicate = q.name.containsIgnoreCase(keyword);

    ObjectCore.ListResult<User> result = ObjectCore.list(q, predicate, pageable);

    // Direkt Page<T> dönüşümü
    return result.toPage(mapper::toResponse);
}
```

### 1.6 Update - Güncelle

```java
public UserResponse update(UUID id, UpdateRequest request) {
    // Önce entity'i getir
    Result<User> result = ObjectCore.getById(User.class, id);

    if (!result.isSuccess()) {
        throw new ResourceNotFoundException("User", id);
    }

    User user = result.getData();

    // Field'ları güncelle
    user.setName(request.getName());
    user.setEmail(request.getEmail());

    // Kaydet
    Result<User> updated = ObjectCore.save(user);

    if (!updated.isSuccess()) {
        throw new BadRequestException(updated.getError());
    }

    return mapper.toResponse(updated.getData());
}
```

### 1.7 Delete - Soft Delete

```java
public void delete(UUID id) {
    Result<User> result = ObjectCore.getById(User.class, id);

    if (!result.isSuccess()) {
        throw new ResourceNotFoundException("User", id);
    }

    Result<Void> deleted = ObjectCore.delete(result.getData());

    if (!deleted.isSuccess()) {
        throw new BadRequestException(deleted.getError());
    }
}

// Entity ile
public void delete(User user) {
    Result<Void> result = ObjectCore.delete(user);

    if (!result.isSuccess()) {
        throw new BadRequestException(result.getError());
    }
}
```

### 1.8 Count & Exists

```java
// Sayma
public long countActiveUsers() {
    QUser q = QUser.user;
    return ObjectCore.count(q, q.active.isTrue().and(q.visible.isTrue()));
}

// Varlık kontrolü
public boolean existsByEmail(String email) {
    QUser q = QUser.user;
    return ObjectCore.exists(q, q.email.eq(email).and(q.visible.isTrue()));
}

// ID ile kontrol
public boolean exists(UUID userId) {
    return ObjectCore.existsById(User.class, userId);
}
```

### 1.9 Batch Save - Toplu Kaydet

```java
// Audit log ile
public void importUsers(List<User> users) {
    ObjectCore.ListResult<User> result = ObjectCore.saveAll(users);

    if (result.getData().isEmpty()) {
        throw new BadRequestException("Import başarısız");
    }

    log.info("{} kullanıcı import edildi", result.getData().size());
}

// Audit log olmadan (performanslı)
public void bulkImport(List<Product> products) {
    ObjectCore.ListResult<Product> result = ObjectCore.saveAllNoAudit(products, 500);

    log.info("{} ürün import edildi", result.getData().size());
}

// Flush count ayarı
public void importLargeData(List<Item> items) {
    // Her 100 kayıtta bir flush
    ObjectCore.saveAll(items, 100, false);
}
```

### 1.10 Native SQL (QueryDSL Yetmediğinde)

```java
// Generic SELECT (Map döner)
public List<Map<String, Object>> getUserRoles() {
    String sql = "SELECT u.name, r.name as role FROM users u JOIN roles r ON u.role_id = r.id WHERE u.status = ?";
    return ObjectCore.nativeQuery(sql, "ACTIVE");
}

// Entity mapping
public List<City> getCities(String countryId) {
    String sql = "SELECT * FROM cities WHERE country_id = ? ORDER BY name";
    return ObjectCore.nativeQuery(sql, City.class, countryId);
}

// INSERT/UPDATE/DELETE
public int bulkUpdatePrices(String category, Double multiplier) {
    String sql = "UPDATE products SET price = price * ? WHERE category = ?";
    return ObjectCore.nativeUpdate(sql, multiplier, category);
}

// Tek değer (COUNT, SUM, MAX, MIN)
public Long getActiveUserCount() {
    String sql = "SELECT COUNT(*) FROM users WHERE active = 1 AND visible = 1";
    return ObjectCore.nativeQueryScalar(sql, Long.class);
}
```

### 1.11 Lookup Cache

```java
// Sık erişilen lookup tabloları için cache
public City getCity(UUID cityId) {
    Result<City> result = ObjectCore.getByIdCached(City.class, cityId);

    if (result.isSuccess()) {
        return result.getData();
    } else {
        throw new ResourceNotFoundException("City", cityId);
    }
}

// Cache'i temizle
public void clearLookupCache() {
    ObjectCore.clearLookupCache();
}

// Tek entity invalidate
public void invalidateCityCache(UUID cityId) {
    ObjectCore.invalidateLookupCache(City.class, cityId);
}
```

### 1.12 Reflection Helper'lar

```java
// Field oku
String name = (String) ObjectCore.getFieldValue(user, "name");
Boolean visible = (Boolean) ObjectCore.getFieldValue(user, "visible");

// Field yaz
ObjectCore.setFieldValue(user, "name", "Yeni İsim");
ObjectCore.setFieldValue(user, "visible", false);

// DTO'dan Entity'ye kopyala
User user = new User();
UserDTO dto = getUserDTO();
ObjectCore.copyProperties(dto, user);

// Deep clone
User original = ObjectCore.getById(User.class, id).getData();
User cloned = ObjectCore.clone(original);
cloned.setName("Kopya");
ObjectCore.save(cloned); // Yeni kayıt olarak kaydedilir
```

---

## 2. Anotasyonlar

### 2.1 @Cacheable

Metodun dönüş değerini cache'e alır.

```java
@Service
public class UserService {

    // Basit cache'leme (5 dakika TTL)
    @Cacheable(value = "users", key = "#id", ttl = 300)
    public UserResponse getById(UUID id) {
        Result<User> result = ObjectCore.getById(User.class, id);

        if (!result.isSuccess()) {
            throw new ResourceNotFoundException("User", id);
        }

        return mapper.toResponse(result.getData());
    }

    // Koşullu cache'leme
    @Cacheable(value = "users", key = "#id", ttl = 600, condition = "#id != null")
    public UserResponse getByIdOrNull(UUID id) {
        Result<User> result = ObjectCore.getById(User.class, id);

        if (result.isSuccess()) {
            return mapper.toResponse(result.getData());
        }
        return null;
    }

    // Unless ile hariç tutma
    @Cacheable(value = "users", key = "#email", unless = "#result == null")
    public UserResponse getByEmail(String email) {
        Result<User> result = ObjectCore.getByField(User.class, "email", email);

        if (result.isSuccess()) {
            return mapper.toResponse(result.getData());
        }
        return null;
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

---

### 2.2 @CacheEvict

Cache'ten değer siler.

```java
@Service
public class UserService {

    // Tek anahtarı sil
    @CacheEvict(value = "users", key = "#id")
    public void delete(UUID id) {
        Result<User> result = ObjectCore.getById(User.class, id);

        if (result.isSuccess()) {
            ObjectCore.delete(result.getData());
        }
    }

    // Tüm cache'i temizle
    @CacheEvict(value = "users", allEntries = true)
    public void clearAllUsersCache() {
        // Tüm "users" cache'i temizlenir
    }

    // Metottan önce sil
    @CacheEvict(value = "users", key = "#id", beforeInvocation = true)
    public void update(UUID id, UpdateRequest request) {
        Result<User> result = ObjectCore.getById(User.class, id);

        if (result.isSuccess()) {
            User user = result.getData();
            user.setName(request.getName());
            ObjectCore.save(user);
        }
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

### 2.3 @AuditLog

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

    // Sadece işlem tipi
    @AuditLog(action = AuditAction.LOGIN)
    public void login(LoginRequest request) {
        // Kullanıcı, IP, User Agent otomatik eklenir
    }
}
```

**Parametreler:**
| Parametre | Tip | Açıklama |
|-----------|-----|----------|
| action | AuditAction | İşlem tipi |
| entityType | String | Entity tipi |
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

### 2.4 @PublishEvent

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

### 2.5 @TimeTest

Metod çalışma süresini test eder.

```java
@Service
public class ProductService {

    // 30ms limit
    @TimeTest(ms = 30)
    public List<Product> getAllProducts() {
        QProduct q = QProduct.product;
        ObjectCore.ListResult<Product> result = ObjectCore.listAll(q, null);
        return result.getData();
        // 30ms'den uzun sürerse WARN log yazılır
    }

    // 100ms limit, ERROR seviyesi
    @TimeTest(ms = 100, level = TimeTest.LogLevel.ERROR)
    public List<Order> getUserOrders(UUID userId) {
        QOrder q = QOrder.order;
        Predicate predicate = q.userId.eq(userId);
        ObjectCore.ListResult<Order> result = ObjectCore.list(q, predicate, null);
        return result.getData();
    }

    // Custom mesaj ile
    @TimeTest(ms = 50, message = "Yavaş sorgu: {method} {actual}ms sürdü!")
    public List<Product> searchProducts(String keyword) {
        QProduct q = QProduct.product;
        Predicate predicate = q.name.containsIgnoreCase(keyword);
        ObjectCore.ListResult<Product> result = ObjectCore.list(q, predicate, null);
        return result.getData();
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
| level | LogLevel | Log seviyesi |
| message | String | Custom mesaj formatı |
| throwOnFailure | boolean | Exception fırlat (varsayılan: false) |

---

### 2.6 @ScheduledJob

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
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        QAuditLogEntity q = QAuditLogEntity.auditLogEntity;
        Predicate predicate = q.executionTime.before(cutoff).and(q.visible.isTrue());

        ObjectCore.ListResult<AuditLogEntity> result = ObjectCore.list(q, predicate, null);

        for (AuditLogEntity log : result.getData()) {
            ObjectCore.delete(log);
        }

        log.info("{} eski audit log temizlendi", result.getData().size());
    }

    // Fixed rate ile (her 5 dakikada)
    @ScheduledJob(
        name = "syncData",
        fixedRate = 300000,
        useLock = true
    )
    public void syncExternalData() {
        // Dış sistem ile veri senkronizasyonu
    }

    // Cluster ortamı için distributed lock
    @ScheduledJob(
        name = "processPayments",
        cron = "0 */10 * * * ?",
        useLock = true,
        lockTimeoutMinutes = 15
    )
    public void processPendingPayments() {
        // Sadece bir instance çalıştırır
    }
}
```

---

### 2.7 @RateLimit

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
        // 2 istek/5 dakika
    }
}
```

---

## 3. Cache Sistemi

### 3.1 Manuel Cache İşlemleri

CacheService ile programatik cache işlemleri:

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final CacheService cacheService;

    // Cache'e al
    public void cacheProduct(UUID productId, ProductResponse response) {
        cacheService.put("products", productId, response, 600);
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
}
```

---

## 4. Audit Logging

### 4.1 Audit Log Sorgulama

```java
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    // Kullanıcıya ait log'lar
    public List<AuditLogEntity> getUserLogs(String userId) {
        QAuditLogEntity q = QAuditLogEntity.auditLogEntity;
        Predicate predicate = q.userId.eq(userId).and(q.visible.isTrue());

        ObjectCore.ListResult<AuditLogEntity> result = ObjectCore.list(q, predicate, null);
        return result.getData();
    }

    // İşlem tipine göre log'lar
    public List<AuditLogEntity> getActionLogs(AuditAction action) {
        QAuditLogEntity q = QAuditLogEntity.auditLogEntity;
        Predicate predicate = q.action.eq(action).and(q.visible.isTrue());

        ObjectCore.ListResult<AuditLogEntity> result = ObjectCore.list(q, predicate, null);
        return result.getData();
    }

    // Entity'ye ait log'lar
    public List<AuditLogEntity> getEntityLogs(String entityClass, String entityId) {
        QAuditLogEntity q = QAuditLogEntity.auditLogEntity;
        Predicate predicate = q.entityClass.eq(entityClass)
                .and(q.entityId.eq(entityId))
                .and(q.visible.isTrue());

        ObjectCore.ListResult<AuditLogEntity> result = ObjectCore.list(q, predicate, null);
        return result.getData();
    }
}
```

---

## 5. Event System

### 5.1 Custom Event Oluşturma

```java
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

// Event listener
@Component
public class UserEventListeners {

    @EventListener
    @Async
    public void handleUserRegistered(UserRegisteredEvent event) {
        UUID userId = event.get("userId", UUID.class);
        String email = event.get("email", String.class);

        // Hoş geldin email'i gönder
        sendWelcomeEmail(email);
    }
}
```

---

## 6. TimeTester

Performans test senaryoları için kullanılır.

```java
@Service
public class PerformanceTestService {

    // Database sorgusu testi
    @TimeTest(ms = 50)
    public List<User> getAllUsers() {
        ObjectCore.ListResult<User> result = ObjectCore.listAll(User.class, null);
        return result.getData();
    }

    // External API çağrısı testi
    @TimeTest(ms = 5000)
    public ExternalData fetchExternalData() {
        return externalApiClient.getData();
    }
}
```

---

## 7. Job Scheduler

### 7.1 Job Örnekleri

```java
@Service
public class ScheduledJobs {

    // Her gece çalışan job'lar
    @ScheduledJob(
        name = "dailyDataCleanup",
        cron = "0 0 1 * * ?",
        useLock = true,
        lockTimeoutMinutes = 120
    )
    public void dailyCleanup() {
        // Eski logları temizle
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        auditService.cleanOldLogs(cutoff);
    }

    // Her 5 dakikada bir
    @ScheduledJob(
        name = "processPendingNotifications",
        fixedRate = 300000,
        useLock = true
    )
    public void processNotifications() {
        notificationService.sendPendingNotifications();
    }
}
```

---

## 8. Notification System

### 8.1 Bildirim Gönderme

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
            "Aramıza hoş geldiniz."
        );
    }

    // Zamanlanmış bildirim
    public void scheduleReminder(UUID userId, LocalDateTime reminderTime) {
        notificationService.createScheduledNotification(
            userId,
            NotificationType.IN_APP,
            "Hatırlatma",
            "Etkinlik yaklaşıyor!",
            reminderTime
        );
    }

    // Okunmamış bildirimleri getir
    public List<Notification> getUnreadNotifications(UUID userId) {
        return notificationService.getUnreadNotifications(userId);
    }

    // Okundu olarak işaretle
    public void markAsRead(UUID notificationId) {
        notificationService.markAsRead(notificationId);
    }
}
```

---

## 9. Rate Limiting

### 9.1 Rate Limit Yapılandırması

```yaml
# application.yaml
rate-limit:
  global:
    capacity: 100
    refill-tokens: 100
    ban-threshold: 15
    key-type: IP_AND_USER
```

### 9.2 Ban Yönetimi

```java
@Service
@RequiredArgsConstructor
public class BanManagementService {

    private final BanCacheService banCacheService;

    public void unbanUser(String ipOrUserId) {
        banCacheService.removeBan(ipOrUserId);
    }

    public boolean isBanned(String ipOrUserId) {
        return banCacheService.isBanned(ipOrUserId);
    }
}
```

---

## 10. File Upload

### 10.1 Dosya Yükleme

```java
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

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

## 11. QueryDSL

### 11.1 Type-Safe Query

```java
@Service
public class UserQueryService {

    public Page<User> search(String keyword, UserStatus status, Pageable pageable) {
        QUser qUser = QUser.user;
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

        ObjectCore.ListResult<User> result = ObjectCore.list(
            qUser,
            BooleanExpression.allOf(predicates.toArray(new Predicate[0])),
            pageable
        );

        return new PageImpl<>(result.getData(), pageable, result.getTotal());
    }
}
```

---

## 12. Pagination & Sorting

### 12.1 PageableRequest Kullanımı

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserResponse>>> list(
            @RequestParam(required = false) String keyword,
            PageableRequest pageRequest
    ) {
        Page<UserResponse> page = userService.list(keyword, pageRequest.toPageable());
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
```

**Client-Side Kullanım:**
```bash
# Query param örnekleri
GET /api/v1/users?page=0&size=20&sort=createdAt&direction=DESC
GET /api/v1/users?page=1&size=50&sort=name&direction=ASC
```

---

## 13. Request Logging

### 13.1 Correlation ID Tracking

Her request için benzersiz correlation ID:

```java
@Service
public class OrderService {

    public void createOrder(CreateOrderRequest request) {
        String correlationId = MDC.get("correlationId");
        log.info("Sipariş oluşturuluyor. Correlation ID: {}", correlationId);
    }
}
```

---

## 14. WebSocket

### 14.1 Client-Side Bağlantı

```javascript
// JWT token ile bağlan
const token = localStorage.getItem('jwtToken');
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

const headers = {
    'Authorization': 'Bearer ' + token
};

stompClient.connect(headers, function (frame) {
    // Broadcast mesajlarını dinle
    stompClient.subscribe('/topic/broadcast', function (message) {
        const data = JSON.parse(message.body);
        console.log('Broadcast:', data);
    });

    // Kişisel mesajları dinle
    stompClient.subscribe('/user/queue/messages', function (message) {
        const data = JSON.parse(message.body);
        console.log('Mesaj:', data);
    });
});
```

### 14.2 Server-Side Kullanım

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

    // Data update bildirimi
    public void notifyDataUpdate(String entityType, UUID entityId) {
        webSocketService.sendDataUpdate(entityType, entityId.toString(), "Veri güncellendi");
    }
}
```

---

## Hızlı Referans

### ObjectCore Hızlı Karar Ağacı

```
├── Tek kayıt save/get/delete?
│   └── ObjectCore.save() / getById() / delete()
│
├── Field ile sorgu?
│   └── ObjectCore.getByField(Entity.class, "fieldName", value)
│
├── Liste sorgu?
│   └── ObjectCore.list(q, predicate, pageable)
│
├── Sayma/varlık kontrolü?
│   └── ObjectCore.count() / exists()
│
├── Toplu kayıt?
│   └── ObjectCore.saveAll(entities)
│
├── Native SQL gerekli?
│   └── ObjectCore.nativeQuery() / nativeUpdate()
│
└── Lookup cache?
    └── ObjectCore.getByIdCached(cls, id)
```

### Anotasyon Karar Ağacı

```
├── Performans testi gerekli mi?
│   └── EVET → @TimeTest(ms=X)
│
├── Cache'lenmeli mi?
│   ├── Okuma → @Cacheable
│   └── Yazma → @CacheEvict
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
└── Rate limit gerekli mi?
    └── Kritik endpoint → @RateLimit
```

---

*Son güncelleme: 2026-03-29*
*Developer: Uğur Işık*
*Version: 2.1 - ObjectCore tabanlı usage*
