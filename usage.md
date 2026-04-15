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

## 15. Transaction Boundary

### 15.1 Nedir?

Transaction boundary, ObjectCore ile veritabanı işlemleri yaparken transaction'ın aktif olmasını garanti eder.

**Problem:**
```java
// ❌ YANLIŞ - Transaction yok
public UserResponse getById(UUID id) {
    Result<User> result = ObjectCore.getById(User.class, id);
    // HATA: "No EntityManager with actual transaction available"
}
```

**Çözüm:**
```java
// ✅ DOĞRU - Transaction var
@Transactional(readOnly = true)
public UserResponse getById(UUID id) {
    Result<User> result = ObjectCore.getById(User.class, id);
    return mapper.toResponse(result.getData());
}
```

### 15.2 Transaction Kontrol Mekanizması

**1. ObjectCoreTransactionAspect**
- ObjectCore metod çağrıldığında devreye girer
- Write operasyonlarında (save, delete, vb.) transaction kontrolü
- Warn log + helper mesaj

**2. TransactionBoundaryFilter**
- Request başında/sonunda transaction leak kontrolü
- Memory leak önleme (EntityManager temizleme)

### 15.3 Kullanım Kuralları

| Context | @Transactional Gerekli mi? |
|---------|---------------------------|
| Service metodu (read) | ✅ `@Transactional(readOnly = true)` |
| Service metodu (write) | ✅ `@Transactional` |
| Controller metodu | ❌ Hayır (Service'i çağırır) |
| Filter metodu | ✅ **Evet** (Transaction scope dışında) |
| @Async metot | ✅ **Evet** (Ayrı thread) |
| @EventListener | ✅ **Evet** (Async ise) |
| @ScheduledJob | ✅ **Evet** (Background thread) |

### 15.4 Konfigürasyon

```yaml
# application.yaml
transaction-boundary:
  strict-mode: false  # Geliştirme: false, Production: true
```

```bash
# JVM argüman
java -jar app.jar --transaction-boundary.strict-mode=true
```

**Strict Mode:**
- `false` (geliştirme): WARN log
- `true` (production): Exception fırlat

### 15.5 Örnek - Filter'da Transaction Kullanımı

```java
@Component
public class MyFilter implements Filter {

    // ❌ YANLIŞ - Transaction yok
    public void doFilter(...) {
        ObjectCore.save(entity);  // HATA!
    }

    // ✅ DOĞRU - Transaction ekle
    @Transactional
    public void doFilter(...) {
        ObjectCore.save(entity);  // Çalışır
    }
}
```

---

## 16. Exception Handling

### 16.1 Custom Exception Oluşturma

```java
/**
 * Kullanıcı bulunamadı exception'ı.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("Kullanıcı bulunamadı: " + userId);
    }

    public UserNotFoundException(String email) {
        super("Kullanıcı bulunamadı: " + email);
    }
}
```

### 16.2 GlobalExceptionHandler'e Ekleme

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage()));
    }
}
```

### 16.3 Mevcut Exception'lar

| Exception | HTTP Status | Kullanım |
|-----------|-------------|----------|
| `ResourceNotFoundException` | 404 | Kaynak bulunamadı |
| `BadRequestException` | 400 | Geçersiz istek |
| `ConflictException` | 409 | Çakışma (duplicate) |
| `UnauthorizedException` | 401 | Yetkisiz erişim |
| `RateLimitException` | 429 | Rate limit aşıldı |
| `RateLimitBanException` | 429 | Banlandı |
| `FileValidationException` | 400 | Dosya validation hatası |
| `FileStorageException` | 500 | Dosya saklama hatası |

### 16.4 Exception Handling Pattern

```java
@Service
public class UserService {

    public UserResponse getById(UUID id) {
        Result<User> result = ObjectCore.getById(User.class, id);

        if (!result.isSuccess()) {
            // Result wrapper'dan hata mesajı al
            throw new ResourceNotFoundException("User", id);
        }

        return mapper.toResponse(result.getData());
    }

    public UserResponse create(CreateRequest request) {
        // Email exists kontrolü
        if (existsByEmail(request.getEmail())) {
            throw new ConflictException("Bu email zaten kayıtlı");
        }

        User user = mapper.toEntity(request);
        Result<User> saved = ObjectCore.save(user);

        if (!saved.isSuccess()) {
            throw new BadRequestException(saved.getError());
        }

        return mapper.toResponse(saved.getData());
    }
}
```

---

## 17. Best Practices

### 17.1 Service Layer

```java
// ✅ DOĞRU
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper mapper;
    // Repository YOK - ObjectCore kullan

    @Transactional
    public UserResponse create(CreateRequest request) {
        // 1. Validation
        validateRequest(request);

        // 2. Entity oluştur
        User user = mapper.toEntity(request);

        // 3. Save
        Result<User> saved = ObjectCore.save(user);
        if (!saved.isSuccess()) {
            throw new BadRequestException(saved.getError());
        }

        // 4. Event publish (opsiyonel)
        // eventPublisher.publish(new UserCreatedEvent(user.getId()));

        return mapper.toResponse(saved.getData());
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        Result<User> result = ObjectCore.getById(User.class, id);

        if (!result.isSuccess()) {
            throw new ResourceNotFoundException("User", id);
        }

        return mapper.toResponse(result.getData());
    }
}
```

### 17.2 Controller Layer

```java
// ✅ DOĞRU
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable UUID id) {
        UserResponse response = userService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateRequest request) {
        UserResponse response = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }
}
```

### 17.3 Entity

```java
// ✅ DOĞRU
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email"),
    @Index(name = "idx_user_visible", columnList = "visible")
})
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;
}
```

### 17.4 DTO

```java
// ✅ DOĞRU - Request DTO
@Data
public class CreateRequest {

    @NotBlank(message = "Email boş olamaz")
    @Email(message = "Geçersiz email formatı")
    private String email;

    @NotBlank(message = "Kullanıcı adı boş olamaz")
    @Size(min = 3, max = 50, message = "Kullanıcı adı 3-50 karakter olmalı")
    private String username;
}

// ✅ DOĞRU - Response DTO
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private UUID id;
    private String email;
    private String username;
    private UserStatus status;
    private LocalDateTime createdAt;
}
```

---

## 18. Performance Tips

### 18.1 Cache Kullanımı

```java
// ✅ Read-heavy endpoint'lerde cache
@Cacheable(value = "users", key = "#id", ttl = 300)
public UserResponse getById(UUID id) {
    // ...
}

// ✅ Write sonrası cache temizle
@CacheEvict(value = "users", key = "#id")
public void delete(UUID id) {
    // ...
}
```

### 18.2 Batch Operations

```java
// ❌ YANLIŞ - Tek tek save
for (User user : users) {
    ObjectCore.save(user);  // N kez DB'ye gider
}

// ✅ DOĞRU - Batch save
ObjectCore.saveAll(users, 100, true);  // 100'er gruplar halinde
```

### 18.3 QueryDSL Optimizasyonu

```java
// ❌ YANLIŞ - Tüm kolonları çek
ObjectCore.list(q, predicate, pageable);

// ✅ DOĞRU - Sadece gerekli kolonlar
queryFactory.select(
    qUser.id,
    qUser.username,
    qUser.email
).from(qUser).where(predicate).fetch();
```

### 18.4 Pagination

```java
// ❌ YANLIŞ - Tümünü çek sonra slice
List<User> all = repository.findAll();  // Memory issue!

// ✅ DOĞRU - Database seviyesinde sayfalama
ObjectCore.list(q, predicate, pageable);  // Sadece o sayfayı çeker
```

---

## 19. Security Best Practices

### 19.1 Password Hashing

```java
// ✅ Spring Security'nin BCrypt kullan
String encoded = passwordEncoder.encode(rawPassword);
// Her seferinde farklı hash üretir (random salt)
```

### 19.2 JWT Token

```java
// ✅ Access token kısa ömürlü
@Value("${jwt.access-token-expiration:900000}") // 15 dakika
private long accessTokenExpiration;

// ✅ Refresh token uzun ömürlü
@Value("${jwt.refresh-token-expiration:604800000}") // 7 gün
private long refreshTokenExpiration;
```

### 19.3 Rate Limiting

```java
// ✅ Kritik endpoint'lerde sıkı limit
@PostMapping("/auth/register")
@RateLimit(capacity = 5, refillTokens = 5, refillDuration = 1, banThreshold = 3)
public ResponseEntity<?> register() {
    // 5 istek/dakika, 3 strike = ban
}

// ✅ Normal endpoint'lerde global limit yeterli
@GetMapping("/users")
public ResponseEntity<?> list() {
    // Global 100 req/dakika
}
```

### 19.4 Input Validation

```java
// ✅ Her request DTO'da validation
@Data
public class CreateRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @Pattern(regexp = "^[A-Za-z0-9]+$")
    private String username;
}
```

---

## 20. Configuration Properties

### 20.1 application.yaml Detaylı

```yaml
# Server
server:
  port: 8080
  servlet:
    context-path: /

# Database
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sinay_core?useSSL=false&serverTimezone=UTC
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  # JPA
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true

  # Redis
  data:
    redis:
      host: localhost
      port: 6379
      password:
      timeout: 60000
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0

# App Custom Properties
app:
  # Admin User
  admin:
    email: admin@sirket.com
    password: GucluSifre123!
    name: Admin
    surname: User

  # JWT
  jwt:
    secret: your-secret-key-min-256-bits
    access-token-expiration: 900000    # 15 dakika (ms)
    refresh-token-expiration: 604800000 # 7 gün (ms)

  # File Upload
  file-upload:
    dir: ./uploads
    max-size: 10485760  # 10MB
    allowed-extensions: jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx

  # Rate Limiting
  rate-limit:
    global:
      capacity: 100
      refill-tokens: 100
      refill-duration: 1  # dakika
      ban-threshold: 15
      ban-duration-minutes: 10
      key-type: IP_AND_USER

  # Transaction Boundary
  transaction-boundary:
    strict-mode: false  # Production: true

  # Mail
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: your-app-password
    from: noreply@sirket.com
    verification:
      base-url: http://localhost:8080

# Logging
logging:
  level:
    com.sinay: INFO
    org.springframework.security: WARN
    org.hibernate.SQL: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [correlationId=%X{correlationId}] %logger{36} - %msg%n"

# Swagger
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
```

---

## 21. Testing

### 21.1 Test Structure

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;  // Test için

    @Test
    void create_shouldReturnUserResponse() {
        // Given
        CreateRequest request = new CreateRequest();
        request.setEmail("test@example.com");
        request.setUsername("testuser");

        // When
        UserResponse response = userService.create(request);

        // Then
        assertNotNull(response.getId());
        assertEquals("test@example.com", response.getEmail());
    }

    @Test
    void getById_whenUserExists_shouldReturnUser() {
        // Given
        User user = User.builder()
                .email("test@example.com")
                .username("testuser")
                .build();
        ObjectCore.save(user);

        // When
        UserResponse response = userService.getById(user.getId());

        // Then
        assertNotNull(response);
        assertEquals("test@example.com", response.getEmail());
    }
}
```

### 21.2 Controller Test

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Test
    void getById_shouldReturnUser() throws Exception {
        // Given
        User user = createUser();
        UUID userId = user.getId();

        // When & Then
        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()));
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        String json = """
            {
                "email": "test@example.com",
                "username": "testuser"
            }
            """;

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }
}
```

---

## 22. Filter'lar

### 22.1 Filter Çalışma Sırası

```
Request → Filter 1 → Filter 2 → Filter 3 → Security → Controller
         (1)        (2)        (3)        (4)
```

### 22.2 RequestLoggingFilter

**Amaç:** Her isteği loglamak, correlation ID oluşturmak.

```java
// Log çıktısı
[abc123] → HTTP GET /api/v1/users from 192.168.1.100 | User-Agent: Mozilla/5.0
[abc123] ← HTTP GET /api/v1/users 200 from 192.168.1.100 in 333ms
```

**Özellikler:**
- Correlation ID header ile client'a geri döner
- Slow request detection (>1000ms = WARN)
- Actuator endpoint'lerini loglamaz

### 22.3 TransactionBoundaryFilter

**Amaç:** Transaction leak detection, memory leak önleme.

**Özellikler:**
- Request başında/sonunda transaction kontrolü
- Transaction leak olursa WARN log + cleanup
- EntityManager holder temizleme

### 22.4 GlobalRateLimitFilter

**Amaç:** Tüm endpoint'lerde rate limiting.

**Özellikler:**
- Token bucket algoritması (100 req/dakika)
- IP + User based key
- Ban management (15 strike = 10 dakika ban)

---

## 23. Common Patterns

### 23.1 CRUD Pattern

```java
@Service
@RequiredArgsConstructor
public class EntityService {

    private final EntityMapper mapper;

    // CREATE
    @Transactional
    public EntityResponse create(CreateRequest request) {
        Entity entity = mapper.toEntity(request);
        Result<Entity> saved = ObjectCore.save(entity);

        if (!saved.isSuccess()) {
            throw new BadRequestException(saved.getError());
        }

        return mapper.toResponse(saved.getData());
    }

    // READ
    @Transactional(readOnly = true)
    public EntityResponse getById(UUID id) {
        Result<Entity> result = ObjectCore.getById(Entity.class, id);

        if (!result.isSuccess()) {
            throw new ResourceNotFoundException("Entity", id);
        }

        return mapper.toResponse(result.getData());
    }

    // UPDATE
    @Transactional
    public EntityResponse update(UUID id, UpdateRequest request) {
        Result<Entity> result = ObjectCore.getById(Entity.class, id);

        if (!result.isSuccess()) {
            throw new ResourceNotFoundException("Entity", id);
        }

        Entity entity = result.getData();
        mapper.updateEntityFromRequest(request, entity);

        Result<Entity> updated = ObjectCore.save(entity);

        if (!updated.isSuccess()) {
            throw new BadRequestException(updated.getError());
        }

        return mapper.toResponse(updated.getData());
    }

    // DELETE
    @Transactional
    public void delete(UUID id) {
        Result<Entity> result = ObjectCore.getById(Entity.class, id);

        if (!result.isSuccess()) {
            throw new ResourceNotFoundException("Entity", id);
        }

        Result<Void> deleted = ObjectCore.delete(result.getData());

        if (!deleted.isSuccess()) {
            throw new BadRequestException(deleted.getError());
        }
    }

    // LIST
    @Transactional(readOnly = true)
    public Page<EntityResponse> list(String keyword, Pageable pageable) {
        QEntity q = QEntity.entity;
        Predicate predicate = q.name.containsIgnoreCase(keyword);

        ListResult<Entity> result = ObjectCore.list(q, predicate, pageable);

        return result.toPage(mapper::toResponse);
    }
}
```

### 23.2 Cache Pattern

```java
@Service
@RequiredArgsConstructor
public class CachedEntityService {

    private final EntityMapper mapper;

    // Cache hit → DB'ye gitmez
    @Cacheable(value = "entities", key = "#id", ttl = 300)
    @Transactional(readOnly = true)
    public EntityResponse getById(UUID id) {
        Result<Entity> result = ObjectCore.getById(Entity.class, id);
        // ...
    }

    // Cache miss → DB'ye gider, sonucu cache'e alır
    // ...
}
```

### 23.3 Async Pattern

```java
@Service
@RequiredArgsConstructor
public class AsyncService {

    private final MailService mailService;
    private final NotificationService notificationService;

    // Async işlem
    @Async
    @Transactional
    public void sendWelcomeEmail(UUID userId) {
        User user = ObjectCore.getById(User.class, userId).getData();
        mailService.sendWelcomeEmail(user.getEmail());
        // transaction gerekli!
    }

    // Event publish async
    @PublishEvent(eventType = UserRegisteredEvent.class)
    public UserResponse register(RegisterRequest request) {
        // Event otomatik async publish edilir
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

### Exception Hierarchy

```
RuntimeException
├── UsException (Base)
│   ├── ResourceNotFoundException (404)
│   ├── BadRequestException (400)
│   ├── ConflictException (409)
│   └── UnauthorizedException (401)
├── RateLimitException (429)
│   └── RateLimitBanException (429)
└── FileUploadException
    ├── FileValidationException (400)
    ├── FileStorageException (500)
    └── UploadedFileNotFoundException (404)
```

### HTTP Status Quick Reference

| Status | Kullanım | Exception |
|--------|----------|-----------|
| 200 | OK | Başarılı |
| 201 | Created | Yeni kayıt |
| 204 | No Content | Delete |
| 400 | Bad Request | Validation, BadRequestException |
| 401 | Unauthorized | Login_required, Invalid_token |
| 403 | Forbidden | Yetkisiz |
| 404 | Not Found | ResourceNotFoundException |
| 409 | Conflict | Duplicate, ConflictException |
| 429 | Too Many Requests | Rate limit, Ban |
| 500 | Internal Error | Server error |

### QueryDSL Quick Reference

```java
// Equality
qUser.email.eq("test@example.com")

// Contains (case-insensitive)
qUser.name.containsIgnoreCase("ahmet")

// AND
qUser.email.eq("test@example.com").and(qUser.active.isTrue())

// OR
qUser.status.eq(UserStatus.ACTIVE).or(qUser.status.eq(UserStatus.PENDING))

// IN
qUser.role.in(Role.ADMIN, Role.MASTER_ADMIN)

// Between
qUser.createdAt.between(startDate, endDate)

// Order
queryFactory.selectFrom(qUser)
    .where(predicate)
    .orderBy(qUser.createdAt.desc())
    .fetch();
```

### Validation Annotations

```java
@NotNull           // null olamaz
@NotBlank          // null veya boş string olamaz
@NotEmpty          // null veya empty collection olamaz
@Size(min=2, max=100)  // String/liste boyutu
@Min(18)           // Minimum değer
@Max(100)          // Maksimum değer
@Email             // Email formatı
@Pattern(regexp="^[A-Z]+$")  // Regex pattern
@Positive          // Pozitif sayı
@NegativeOrZero    // Sıfır veya negatif
@Past              // Geçmiş tarih
@Future            // Gelecek tarih
@AssertTrue        // true olmalı
@AssertFalse       // false olmalı
```

### Pagination Quick Reference

```java
// Controller
@GetMapping
public ResponseEntity<ApiResponse<Page<Response>>> list(
        PageableRequest pageRequest
) {
    Page<Response> page = service.list(pageRequest.toPageable());
    return ResponseEntity.ok(ApiResponse.ok(page));
}

// Service
@Transactional(readOnly = true)
public Page<Response> list(Pageable pageable) {
    QEntity q = QEntity.entity;
    Predicate predicate = q.visible.isTrue();

    ListResult<Entity> result = ObjectCore.list(q, predicate, pageable);
    return result.toPage(mapper::toResponse);
}

// Client query
GET /api/v1/entities?page=0&size=20&sort=createdAt&direction=DESC
```

### JWT Token Flow

```
1. Register/Login → Access Token (15dk) + Refresh Token (7 gün)
                ↓
2. API Request → Bearer <Access Token>
                ↓
3. Access Token Expired → 401 Unauthorized
                ↓
4. Refresh Token → New Access Token
                ↓
5. Refresh Token Expired → Re-login required
```

---

## Tips & Tricks

### Tip 1: ObjectCore Result Kontrolü

```java
// ❌ YANLIŞ - Result kontrolü yok
ObjectCore.save(entity);

// ✅ DOĞRU - Result kontrolü
Result<User> result = ObjectCore.save(user);
if (!result.isSuccess()) {
    throw new BadRequestException(result.getError());
}
```

### Tip 2: Transaction Unutma

```java
// ❌ YANLIŞ - Transaction yok
public UserResponse getById(UUID id) {
    Result<User> result = ObjectCore.getById(User.class, id);
}

// ✅ DOĞRU - Transaction var
@Transactional(readOnly = true)
public UserResponse getById(UUID id) {
    Result<User> result = ObjectCore.getById(User.class, id);
}
```

### Tip 3: QueryDSL Visible Filtresi

```java
// ❌ YANLIŞ - Visible filtresi unutulmuş
Predicate predicate = qUser.email.eq(email);

// ✅ DOĞRU - Visible her zaman filtrelenir
Predicate predicate = qUser.email.eq(email).and(qUser.visible.isTrue());
```

### Tip 4: Cache Evict

```java
// ❌ YANLIŞ - Update sonrası cache temizlenmedi
public UserResponse update(UUID id, UpdateRequest request) {
    // ...
}

// ✅ DOĞRU - Update sonrası cache temizlenir
@CacheEvict(value = "users", key = "#id")
public UserResponse update(UUID id, UpdateRequest request) {
    // ...
}
```

### Tip 5: Async Transaction

```java
// ❌ YANLIŞ - Async metodda transaction yok
@Async
public void processAsync(UUID userId) {
    ObjectCore.save(entity);  // HATA!
}

// ✅ DOĞRU - Async metodda transaction var
@Async
@Transactional
public void processAsync(UUID userId) {
    ObjectCore.save(entity);  // Çalışır
}
```

---

*Son güncelleme: 2026-04-15*
*Developer: Uğur Işık*
*Version: 2.1 - ObjectCore tabanlı usage*
