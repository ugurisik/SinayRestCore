# SinayRestCore

<div align="center">

**Yeniden Kullanılabilir REST API Core Framework**

Spring Boot 4 tabanlı, production ready, genişletilebilir REST API altyapısı.

Yeni projelerde temel yapı olarak kullanılmak üzere tasarlanmıştır.

[![Spring](https://img.shields.io/badge/Spring%20Boot-4.0.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## <a name="hakkinda"></a> 📋 Hakkında

**SinayRestCore**, yeni REST API projeleri için hızlı başlangıç sağlayan, **core framework** olarak tasarlanmış bir projedir.

### Core Framework Olarak Kullanımı

Bu proje, diğer REST API projelerinizde **temel yapı** olarak kullanılmak üzere geliştirilmiştir:

- ✅ **Hazır Auth Sistemi** — JWT, refresh token, role-based access
- ✅ **Audit Logging** — Otomatik işlem kaydı
- ✅ **Rate Limiting** — Token bucket algoritması
- ✅ **WebSocket Desteği** — Real-time bildirimler
- ✅ **Cache Sistemi** — Redis + Spring Cache abstraction
- ✅ **File Upload** — Çoklu dosya yükleme
- ✅ **Notification** — Email, SMS, Push, In-app bildirimler
- ✅ **Event System** — Domain events ile loose coupling
- ✅ **Job Scheduler** — Distributed job management
- ✅ **ObjectCore** — Repository'siz CRUD operasyonları

### Nasıl Kullanılır?

```
┌─────────────────────────────────────────────────────────────┐
│                    Yeni Proje Başlat                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. SinayRestCore'ı kopyala                                 │
│  2. groupId ve artifactId'yi değiştir                       │
│  3. Yeni entity'lerini ekle                                  │
│  4. Business logic'ı yaz                                    │
│  5. Deploy                                                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

> **Detaylı kullanım için:** [**usage.md**](usage.md) dosyasına bakın.

---

## <a name="ozellikler"></a> 🚀 Özellikler

| Özellik | Açıklama |
|---------|----------|
| **JWT Auth** | Access + Refresh token, otomatik yenileme |
| **Role-Based Access** | MASTER_ADMIN, ADMIN, USER rolleri |
| **Rate Limiting** | Global + endpoint-specific rate limit |
| **Audit Logging** | Entity ve operasyon loglama |
| **WebSocket** | JWT auth ile real-time mesajlaşma |
| **Cache** | Redis + Caffeine (in-memory) |
| **File Upload** | Multi-provider (local, S3, Azure) |
| **Notification** | Email, SMS, Push, In-app |
| **Event System** | Domain events + async listeners |
| **Job Scheduler** | Cron + distributed lock |
| **QueryDSL** | Type-safe sorgular |
| **Pagination** | Standard PageableRequest |
| **Request Logging** | Correlation ID ile takip |

---

## <a name="stack"></a> 🛠 Tech Stack

| Katman | Teknoloji |
|--------|----------|
| **Framework** | Spring Boot 4.0.4 |
| **Java** | 17 |
| **ORM** | Spring Data JPA + Hibernate |
| **Query** | QueryDSL 5.1.0 |
| **Auth** | JWT (jjwt 0.12) + Spring Security 6 |
| **Database** | MySQL 8 (test: H2) |
| **Cache** | Redis + Caffeine |
| **WebSocket** | STOMP + SockJS |
| **Mail** | Spring Mail + Thymeleaf |
| **Mapping** | MapStruct |
| **Boilerplate** | Lombok |
| **Build** | Maven |

---

## <a name="hizli-baslangic"></a> ⚡ Hızlı Başlangıç

### 1. Veritabanını Oluştur

```sql
CREATE DATABASE sinay_core CHARACTER SET utf8mb4 COLLATE utf8mb4_turkish_ci;
```

### 2. Konfigürasyon

`src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sinay_core?useSSL=false&serverTimezone=UTC
    username: root
    password: sifren

app:
  admin:
    email: admin@sirket.com
    password: GucluSifre123!
    name: Admin
    surname: User
```

### 3. Uygulamayı Başlat

```bash
mvn clean install
mvn spring-boot:run
```

İlk başlatmada otomatik olarak:
- `MASTER_ADMIN`, `ADMIN`, `USER` rolleri oluşturulur
- `application.yaml`'deki bilgilerle MASTER_ADMIN kullanıcısı oluşturulur

### 4. Test Et

```bash
# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identifier":"admin@sirket.com","password":"GucluSifre123!"}'

# Dönen access token ile:
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <access_token>"
```

---

## <a name="api-endpoints"></a> 📡 API Endpoint'leri

### Swagger/OpenAPI Dokümantasyonu

```
GET    /swagger-ui.html            Swagger UI (Interaktif API dokümantasyonu)
GET    /v3/api-docs                OpenAPI 3.0 JSON spec
```

### Authentication — Public

```
POST   /api/v1/auth/register          Kayıt ol
POST   /api/v1/auth/login             Giriş yap → JWT token
POST   /api/v1/auth/refresh           Refresh token ile yeni access token
POST   /api/v1/auth/logout            Çıkış
GET    /api/v1/auth/verify-email      Email doğrulama
POST   /api/v1/auth/forgot-password   Şifre sıfırlama maili
POST   /api/v1/auth/reset-password    Yeni şifre belirle
```

### User — Auth Gerekli

```
GET    /api/v1/users/me               Kendi profilim
PATCH  /api/v1/users/me               Profil güncelle
POST   /api/v1/users/me/change-password Şifre değiştir
```

### Admin — MASTER_ADMIN Only

```
GET    /api/v1/admin/users            Tüm kullanıcılar (sayfalı)
GET    /api/v1/admin/users/{id}       Tek kullanıcı
POST   /api/v1/admin/users/{id}/roles/{role} Rol ata
DELETE /api/v1/admin/users/{id}/roles/{role} Rol kaldır
POST   /api/v1/admin/users/{id}/lock   Hesap kilitle
POST   /api/v1/admin/users/{id}/unlock Kilit aç
DELETE /api/v1/admin/users/{id}       Soft delete
```

### WebSocket — Auth Gerekli

```
WS     /ws                           WebSocket endpoint (STOMP)
```

### File Upload — Auth Gerekli

```
POST   /api/v1/files/upload           Dosya yükle
GET    /api/v1/files/download/{id}    Dosya indir
DELETE /api/v1/files/{id}             Dosya sil
```

---

## <a name="response-format"></a> 📦 Response Format

### Başarılı Response

```json
{
  "success": true,
  "message": "İlem başarılı",
  "data": { ... },
  "timestamp": "2026-03-29T12:00:00"
}
```

### Hata Response

```json
{
  "success": false,
  "message": "Validasyon hatası",
  "errors": {
    "email": "Geçerli bir email giriniz"
  },
  "timestamp": "2026-03-29T12:00:00"
}
```

### Sayfalı Response

```json
{
  "success": true,
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5,
    "first": true,
    "last": false
  }
}
```

---

## <a name="yeni-proje"></a> 📁 Yeni Proje İçin Kullanım

### Adım 1: Projeyi Kopyala

```bash
# Yeni proje klasörü oluştur
cp -r SinayRestCore YeniProje
cd YeniProje

# pom.xml'de groupId ve artifactId'yi değiştir
# package'ları rename et: com.sinay.core → com.sirket.proje
```

### Adım 2: Yeni Entity Ekle

```java
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_name", columnList = "name"),
    @Index(name = "idx_product_visible", columnList = "visible")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "stock", nullable = false)
    private Integer stock;
}
```

### Adım 3: QueryDSL Q-Class'ı Üret

```bash
mvn clean compile
# target/generated-sources/annotations/ altında QProduct.java oluşur
```

IDE'de `target/generated-sources/annotations` klasörünü **Sources Root** olarak işaretle.

### Adım 4: Service ve Controller Yaz

```java
// Service
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper mapper;

    @Transactional
    public ProductResponse create(CreateRequest request) {
        Product product = mapper.toEntity(request);
        ObjectCore.Result<Product> saved = ObjectCore.save(product);
        return mapper.toResponse(saved.getData());
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(String keyword, Pageable pageable) {
        QProduct q = QProduct.product;
        Predicate predicate = q.name.containsIgnoreCase(keyword);

        ObjectCore.ListResult<Product> result = ObjectCore.list(q, predicate, pageable);

        return new PageImpl<>(
            result.getData().stream().map(mapper::toResponse).toList(),
            pageable,
            result.getTotal()
        );
    }
}
```

```java
// Controller
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> list(
            @RequestParam(required = false) String keyword,
            PageableRequest pageRequest
    ) {
        Page<ProductResponse> page = productService.list(keyword, pageRequest.toPageable());
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateRequest request) {
        ProductResponse response = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }
}
```

### Adım 5: Yeni Rol Ekle (İsteğe Bağlı)

```java
// Role.java enum'ına ekle
public enum RoleName {
    MASTER_ADMIN,
    ADMIN,
    USER,
    MODERATOR  // Yeni rol
}

// Controller'da kullan
@PreAuthorize("hasAnyRole('MASTER_ADMIN', 'MODERATOR')")
@GetMapping("/moderated-content")
public ResponseEntity<?> getContent() {
    return ResponseEntity.ok(ApiResponse.ok("Content"));
}
```

---

## <a name="dokumantasyon"></a> 📚 Dokümantasyon

| Dosya | İçerik |
|-------|--------|
| **[usage.md](usage.md)** | Detaylı kullanım kılavuzu, anotasyonlar, örnekler |
| **[pom.xml](pom.xml)** | Maven bağımlılıkları |

### usage.md İçeriği

- **Anotasyonlar** — @Cacheable, @AuditLog, @PublishEvent, @TimeTest, @ScheduledJob, @RateLimit
- **Cache Sistemi** — Manuel cache işlemleri
- **Audit Logging** — Log sorgulama
- **Event System** — Custom event'ler
- **Job Scheduler** — Scheduled job örnekleri
- **Notification System** — Bildirim gönderme
- **Pagination & Sorting** — PageableRequest kullanımı
- **Request Logging** — Correlation ID tracking
- **WebSocket** — Real-time mesajlaşma

---

## <a name="filter-structure"></a> 🔍 Filter Yapısı

Her HTTP isteği şu filter'lardan geçer:

```
┌─────────────────────────────────────────────────────────────────┐
│                     HTTP Request                                 │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  1. RequestLoggingFilter                                        │
│     • Correlation ID oluşturur                                  │
│     • İstek/response loglar                                     │
│     • Performance tracking (>1000ms = WARN)                     │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. TransactionBoundaryFilter                                   │
│     • Transaction leak detection                                │
│     • Memory leak önleme                                        │
│     • @Transactional kontrolü                                   │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. GlobalRateLimitFilter                                       │
│     • Token bucket rate limiting (100 req/dakika)              │
│     • Ban management (15 strike = 10 dakika ban)               │
│     • IP + User based key                                      │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  4. JwtAuthenticationFilter (Spring Security içinde)            │
│     • JWT token doğrulama                                       │
│     • User details yükleme                                      │
│     • Security context ayarlama                                 │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Controller                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## <a name="transaction-boundary"></a> 🔄 Transaction Boundary

### ObjectCore ve Transaction İlişkisi

**Kural:** ObjectCore metodları çağıran her Service metodu `@Transactional` olmalıdır.

```java
// ❌ YANLIŞ - Transaction yok, HATA verir!
public UserResponse getById(UUID id) {
    Result<User> result = ObjectCore.getById(User.class, id);
    // HATA: "No EntityManager with actual transaction available"
}

// ✅ DOĞRU - Transaction var
@Transactional(readOnly = true)
public UserResponse getById(UUID id) {
    Result<User> result = ObjectCore.getById(User.class, id);
    return mapper.toResponse(result.getData());
}
```

### Transaction Koruma Mekanizması

**1. ObjectCoreTransactionAspect (AspectJ)**
- ObjectCore çağrıldığında transaction kontrolü
- Write operasyonlarında (save, delete, vb.) WARN log
- Strict mode'da exception fırlatır

**2. TransactionBoundaryFilter (Servlet Filter)**
- Request başında/sonunda transaction kontrolü
- Transaction leak detection
- Memory leak önleme (EntityManager temizleme)

### Konfigürasyon

```yaml
# application.yaml
transaction-boundary:
  strict-mode: false  # Geliştirme: false, Production: true
```

```bash
# JVM argüman ile
java -jar app.jar --transaction-boundary.strict-mode=true
```

---

## <a name="environment-variables"></a> 🔧 Environment Variables

`.env` dosyası veya environment variables ile konfigürasyon:

```bash
# Database
DB_URL=jdbc:mysql://localhost:3306/sinay_core
DB_USERNAME=root
DB_PASSWORD=password

# JWT
JWT_SECRET=your-secret-key-min-256-bits
JWT_ACCESS_TOKEN_EXPIRATION=900000    # 15 dakika (ms)
JWT_REFRESH_TOKEN_EXPIRATION=604800000 # 7 gün (ms)

# Redis (Cache)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Admin User (İlk başlatma için)
ADMIN_EMAIL=admin@sirket.com
ADMIN_PASSWORD=GucluSifre123!
ADMIN_NAME=Admin
ADMIN_SURNAME=User

# File Upload
FILE_UPLOAD_DIR=./uploads
FILE_MAX_SIZE=10485760  # 10MB (byte)

# Rate Limiting
RATE_LIMIT_CAPACITY=100
RATE_LIMIT_REFILL_TOKENS=100
RATE_LIMIT_BAN_THRESHOLD=15

# Transaction Boundary
TRANSACTION_BOUNDARY_STRICT_MODE=false  # Production: true

# Mail (SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_FROM=noreply@sirket.com
```

---

## <a name="error-codes"></a> 🚨 Hata Kodları

| HTTP Status | Error Code | Açıklama |
|-------------|------------|----------|
| 400 | VALIDATION_ERROR | Validasyon hatası |
| 400 | BAD_REQUEST | Geçersiz istek |
| 401 | UNAUTHORIZED | Kimlik doğrulama yok |
| 401 | INVALID_TOKEN | Geçersiz JWT token |
| 401 | TOKEN_EXPIRED | Token süresi dolmuş |
| 403 | FORBIDDEN | Yetkisiz erişim |
| 403 | ACCOUNT_LOCKED | Hesap kilitli |
| 404 | RESOURCE_NOT_FOUND | Kaynak bulunamadı |
| 409 | CONFLICT | Çakışma (duplicate vb.) |
| 429 | RATE_LIMIT_EXCEEDED | Rate limit aşıldı |
| 429 | TOO_MANY_REQUESTS | Çok fazla istek |
| 429 | BANNED | IP/Kullanıcı banlandı |
| 500 | INTERNAL_ERROR | Internal server error |

---

## <a name="troubleshooting"></a> 🔧 Troubleshooting

### "No EntityManager with actual transaction available"

**Sorun:** ObjectCore çağrıldığında transaction yok.

**Çözüm:**
```java
@Service
public class UserService {
    // @Transactional ekleyin
    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        Result<User> result = ObjectCore.getById(User.class, id);
        // ...
    }
}
```

### "Connection refused" Hatası

**Sorun:** Database/Redis başlatılmamış.

**Çözüm:**
```bash
# MySQL başlat
brew services start mysql  # macOS
systemctl start mysql     # Linux

# Redis başlat
redis-server
```

### Q-Class Bulunamıyor

**Sorun:** QueryDSL Q-class'ları generate edilmemiş.

**Çözüm:**
```bash
mvn clean compile
# IDE'de target/generated-sources/annotations'u Sources Root olarak işaretle
```

### Rate Limit Banlandı

**Sorun:** Çok fazla istek atıldı.

**Çözüm:**
```bash
# Ban'ı manuel kaldır (Redis)
redis-cli FLUSHDB
# Veya bekleyin (otomatik kalkar)
```

### JWT Token Refresh Sorunu

**Sorun:** Refresh token süresi doldu.

**Çözüm:** Yeniden login yapın:
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identifier":"email","password":"password"}'
```

---

## <a name="performance-tuning"></a> ⚡ Performance Tuning

### Database

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
```

### Cache

```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 300000  # 5 dakika
      cache-null-values: false
```

### Rate Limiting

```yaml
rate-limit:
  global:
    capacity: 200           # Production için artır
    refill-tokens: 200
    ban-threshold: 20
```

### JVM Args

```bash
java -Xms512m -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar app.jar
```

---

## <a name="testing"></a> 🧪 Testing

### Test Çalıştırma

```bash
# Tüm testler
mvn test

# Sadece bir test sınıfı
mvn test -Dtest=UserServiceTest

# Sadece bir test metodu
mvn test -Dtest=UserServiceTest#testCreateUser

# Coverage raporu
mvn clean test jacoco:report
# Rapor: target/site/jacoco/index.html
```

### Test Konfigürasyonu

`src/test/resources/application-test.yaml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    hibernate:
      ddl-auto: create-drop
```

---

## <a name="contributing"></a> 🤝 Contributing

1. Fork yapın
2. Feature branch oluşturun (`git checkout -b feature/amazing-feature`)
3. Commit yapın (`git commit -m 'feat: Add amazing feature'`)
4. Push edin (`git push origin feature/amazing-feature`)
5. Pull Request açın

### Commit Convention

```
feat:     Yeni özellik
fix:      Bug fix
docs:     Dokümantasyon
style:    Kod formatı
refactor: Refactoring
test:     Test
chore:    Build/process
```

---

## <a name="onemli-notlar"></a> ⚠️ Önemli Notlar

| Konu | Açıklama |
|------|----------|
| **DDL** | `ddl-auto=update` — Entity değişince DB otomatik güncellenir |
| **Soft Delete** | `visible=false` yapılır, kayıt DB'den silinmez |
| **Async Mail** | Mail gönderimi ayrı thread'de, API'yi bloklamaz |
| **Email Enumeration** | `/forgot-password` kullanıcı yoksa da aynı mesajı döner |
| **Refresh Rotation** | Her refresh işleminde eski token geçersiz kılınır |
| **ObjectCore** | Repository yerine ObjectCore kullanın (statik utility) |
| **QueryDSL** | String query YASAK — type-safe Q-class kullanın |

---

## <a name="test"></a> 🧪 Test

```bash
# Test çalıştır (H2 in-memory DB)
mvn test

# Test + Coverage
mvn clean test jacoco:report
```

Test sonucu `target/surefire-reports/` klasöründe.

---

## <a name="changelog"></a> 📝 Changelog

### Version 2.1 (2026-03-29)

**Yeni Özellikler:**
- ✨ TransactionBoundaryFilter - Transaction leak detection
- ✨ ObjectCoreTransactionAspect - Transaction kontrolü
- ✨ RequestLoggingFilter - Correlation ID ile request tracking
- ✨ GlobalRateLimitFilter - Token bucket rate limiting
- ✨ WebSocket support - JWT authenticated real-time messaging
- ✨ File Upload module - Multi-provider file storage
- ✨ Cache system - Redis + Caffeine hybrid
- ✨ Audit Logging - Automatic entity/operation logging
- ✨ Event System - Domain events with @PublishEvent
- ✨ Job Scheduler - Distributed scheduled jobs
- ✨ Notification System - Email, SMS, Push, In-app
- ✨ TimeTest annotation - Method performance testing

**İyileştirmeler:**
- 🚀 ObjectCore ile repository'siz CRUD
- 🚀 PageableRequest standardization
- 🚀 Native SQL support (4 yeni metod)
- 🚀 Lookup cache (Caffeine)
- 🚀 Spring Page dönüşümü (toPage)

**Bug Fixes:**
- 🐛 Circular reference audit log fix
- 🐛 EntityManager thread-safety
- 🐛 Memory leak prevention

---

## <a name="roadmap"></a> 🗺️ Roadmap

### v2.2 (Planlanan)
- [ ] GraphQL Support
- [ ] Multi-tenancy
- [ ] Elasticsearch integration
- [ ] Distributed tracing (OpenTelemetry)
- [ ] Metrics (Prometheus + Grafana)

### v3.0 (Gelecek)
- [ ] Microservices support
- [ ] Event sourcing
- [ ] CQRS pattern
- [ ] gRPC support
- [ ] Kubernetes Helm charts

---

## <a name="architecture"></a> 🏗️ Architecture

### Layer Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      Presentation Layer                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Controller  │  │  DTO Mapper  │  │  Validators  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                      Business Layer                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │    Service   │  │  ObjectCore  │  │  Event Pub.  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Cache      │  │    Audit     │  │  Rate Limit  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                      Data Access Layer                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   JPA/Hiber. │  │  QueryDSL    │  │  Redis       │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                      Storage Layer                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   MySQL      │  │  File System │  │   S3/Azure   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### Cross-Cutting Concerns

```
┌─────────────────────────────────────────────────────────────┐
│                    Cross-Cutting Concerns                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Security      │ JWT, Role-Based, Rate Limiting      │   │
│  │  Logging       │ Request, Audit, Correlation ID      │   │
│  │  Caching       │ Redis, Caffeine, Lookup Cache       │   │
│  │  Validation    │ Input, Business, Output             │   │
│  │  Events        │ Domain Events, Async Listeners      │   │
│  │  Transactions  │ @Transactional, Boundary Checks     │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## <a name="lisans"></a> 📜 Lisans

MIT License — Detaylar için [LICENSE](LICENSE) dosyasına bakın.

---

## <a name="iletisim"></a> 📧 İletişim

**Developer:** Uğur Işık

*Son güncelleme: 2026-04-15*
*Version: 2.1*
