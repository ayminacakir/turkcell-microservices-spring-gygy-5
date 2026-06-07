# Transactional Outbox Pattern ve Idempotency Ders Notları

Bu derste mikroservislerde Kafka ile event gönderirken **veri kaybı yaşamamak**, Kafka kapalı olsa bile eventleri saklamak ve consumer tarafında aynı event iki kez gelse bile tekrar işlememek için iki önemli konu işlendi:

- **Transactional Outbox Pattern**
- **Idempotency / Inbox Pattern**

Derste örnek olarak iki servis üzerinden ilerledik:

- `product-service`
- `user-service`

`product-service` event üreten servis oldu.  
`user-service` ise Kafka’dan event tüketen servis oldu.

---

## 1. Neden product-service ve user-service için ayrı database açtık?

Mikroservis mimarisinde her servisin kendi veritabanına sahip olması tercih edilir. Buna genelde **Database per Service** yaklaşımı denir.

Bu derste:

- `product-service` kendi veritabanına bağlandı.
- `user-service` kendi veritabanına bağlandı.

Çünkü iki servis de kendi tablolarıyla çalışıyor.

Örneğin:

- `product-service` içinde `outbox` tablosu var.
- `user-service` içinde `processed_events` tablosu var.

Bu yüzden ikisinin de ayrı bir database’i olması gerekiyor.

---

## 2. Docker Compose ile iki ayrı PostgreSQL database ayağa kaldırma

Kafka ve Kafka UI için zaten bir `docker-compose.yml` dosyamız vardı. Bu dosyaya ayrıca iki tane PostgreSQL servisi ekledik:

- `product-db`
- `user-db`

```yaml
product-db:
  image: postgres:17                         # Kullanılacak PostgreSQL image versiyonu
  container_name: product-db                 # Docker container adı
  environment:                               # Container çalışırken kullanılacak ortam değişkenleri
    POSTGRES_USER: postgres                  # Veritabanı kullanıcı adı
    POSTGRES_PASSWORD: test12345             # Veritabanı şifresi
    POSTGRES_DB: products                    # Container ilk açıldığında oluşturulacak database adı
  ports:
    - "5433:5432"                            # Sol taraf bilgisayarın portu, sağ taraf container içindeki PostgreSQL portu
  volumes:
    - product-db-data:/var/lib/postgresql/data # Veritabanı dosyalarını kalıcı tutan volume

user-db:
  image: postgres:17                         # Kullanılacak PostgreSQL image versiyonu
  container_name: user-db                    # Docker container adı
  environment:
    POSTGRES_USER: postgres                  # Veritabanı kullanıcı adı
    POSTGRES_PASSWORD: test12345             # Veritabanı şifresi
    POSTGRES_DB: users                       # Container ilk açıldığında oluşturulacak database adı
  ports:
    - "5434:5432"                            # Localde 5434, container içinde 5432 portuna bağlanır
  volumes:
    - user-db-data:/var/lib/postgresql/data  # User database verilerini kalıcı tutar
```

---

## 3. Docker port mantığı

Docker port yazımı şu şekildedir:

```text
LOCAL_PORT:CONTAINER_PORT
```

Örneğin:

```yaml
ports:
  - "5433:5432"
```

Burada:

| Kısım | Anlamı |
|---|---|
| `5433` | Benim bilgisayarımdaki port |
| `5432` | Docker container içindeki PostgreSQL portu |

PostgreSQL container içinde her zaman kendi varsayılan portu olan `5432` ile çalışır.  
Ama aynı bilgisayarda iki PostgreSQL container çalıştıracaksak ikisini de localde `5432` portuna bağlayamayız. Çünkü aynı port iki container tarafından aynı anda kullanılamaz.

Bu yüzden:

```text
product-db -> localhost:5433 üzerinden erişilir
user-db    -> localhost:5434 üzerinden erişilir
```

Ama containerların kendi içindeki PostgreSQL portu yine `5432`dir.

---

## 4. Volume nedir? Neden kullandık?

Docker container silinirse içindeki veriler de normalde silinebilir.  
Database için bu istenmez. Çünkü database içinde kayıtlarımız var.

Bu yüzden `volume` kullanırız.

```yaml
volumes:
  - product-db-data:/var/lib/postgresql/data
```

Buradaki mantık şudur:

- PostgreSQL verileri container içinde `/var/lib/postgresql/data` klasöründe saklar.
- Biz bu klasörü Docker volume’a bağlıyoruz.
- Böylece container silinse bile database verileri volume içinde kalır.
- Container tekrar ayağa kalkınca eski verileri kullanmaya devam eder.

Kısaca:

> Volume, database verilerinin container dışında kalıcı şekilde saklanmasını sağlar.

---

## 5. Docker Compose’u çalıştırma

Bu servisleri ayağa kaldırmak için şu komutu kullandık:

```bash
docker compose up -d
```

Buradaki `-d` şu anlama gelir:

```text
Detached mode
```

Yani containerlar terminali meşgul etmeden arka planda çalışır.

---

## 6. Spring Boot database connection ayarları

Database containerları ayağa kalktıktan sonra servislerin `application.yml` dosyalarında datasource ayarları yapıldı.

### product-service datasource

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/products # Product DB'ye local bilgisayardan bağlanıyoruz
    username: postgres                             # Docker compose içindeki POSTGRES_USER
    password: test12345                            # Docker compose içindeki POSTGRES_PASSWORD
    driver-class-name: org.postgresql.Driver       # PostgreSQL JDBC driver sınıfı
```

### user-service datasource

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5434/users    # User DB'ye local bilgisayardan bağlanıyoruz
    username: postgres                             # Kullanıcı adı
    password: test12345                            # Şifre
    driver-class-name: org.postgresql.Driver       # PostgreSQL driver
```

Burada `localhost` yazmamızın sebebi şudur:

Spring Boot uygulamasını kendi bilgisayarımızdan çalıştırıyoruz.  
Yani uygulama Docker container içinde değil, local makinede çalışıyor.

Bu yüzden local makineden Docker container’a bağlanırken dış portları kullanırız:

```text
product-service -> localhost:5433/products
user-service    -> localhost:5434/users
```

---

## 7. JPA ddl-auto update ayarı

Sonra JPA için şu ayarı ekledik:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

Bu ayar şunu yapar:

- Entity sınıflarına bakar.
- Database’de karşılık gelen tablo yoksa oluşturur.
- Yeni alan eklenirse tabloyu güncellemeye çalışır.

Örneğin `OutboxEvent` entity’sini yazınca `outbox` tablosu otomatik oluşabilir.

Ders ve geliştirme ortamında kullanışlıdır.  
Ama gerçek projelerde genellikle migration tool kullanılır. Örneğin:

- Flyway
- Liquibase

---

## 8. pgAdmin neden eklendi?

Docker Compose’a pgAdmin de eklendi:

```yaml
pgadmin:
  image: dpage/pgadmin4:latest                 # pgAdmin arayüzünün Docker image'ı
  container_name: pgadmin-gygy5                # Container adı
  environment:
    PGADMIN_DEFAULT_EMAIL: admin@admin.com     # pgAdmin giriş emaili
    PGADMIN_DEFAULT_PASSWORD: admin            # pgAdmin giriş şifresi
  ports:
    - "5050:80"                                # Localde 5050 portundan pgAdmin arayüzüne girilir
```

pgAdmin, PostgreSQL veritabanlarını görsel olarak yönetmek için kullanılır.

Yani terminalden `psql` yazmak yerine tarayıcıdan şu işleri yapabiliriz:

- Database tablolarını görmek
- Tablodaki kayıtları incelemek
- SQL query çalıştırmak
- `outbox` ve `processed_events` tablolarını kontrol etmek

Tarayıcıdan giriş:

```text
http://localhost:5050
```

Giriş bilgileri:

```text
Email: admin@admin.com
Password: admin
```

---

## 9. pgAdmin bağlantısında neden localhost değil product-db yazıyoruz?

Burası kafa karıştırıcı ama önemli.

Spring Boot uygulaması local bilgisayarda çalışıyorsa database’e şöyle bağlanır:

```text
localhost:5433
```

Çünkü local bilgisayardan Docker container’a dışarı açılan porttan giriyoruz.

Ama pgAdmin de Docker container içinde çalışıyor. Yani pgAdmin local makinede değil, Docker network içinde.

Docker Compose içindeki containerlar birbirleriyle servis adı üzerinden konuşabilir.

Bu yüzden pgAdmin’den server eklerken:

```text
Host: product-db
Port: 5432
```

yazılır.

Çünkü pgAdmin containerı, `product-db` containerını Docker network içinde bu isimle bulur.

### Local uygulamadan bağlanırken

```text
Host: localhost
Port: 5433
```

### pgAdmin containerından bağlanırken

```text
Host: product-db
Port: 5432
```

Aynı mantık `user-db` için de geçerli:

### Local uygulamadan

```text
Host: localhost
Port: 5434
```

### pgAdmin’den

```text
Host: user-db
Port: 5432
```

Özet:

| Bağlanan taraf | Host | Port |
|---|---:|---:|
| Local Spring Boot -> product-db | `localhost` | `5433` |
| pgAdmin container -> product-db | `product-db` | `5432` |
| Local Spring Boot -> user-db | `localhost` | `5434` |
| pgAdmin container -> user-db | `user-db` | `5432` |

---

# Transactional Outbox Pattern

## 10. Outbox Pattern nedir?

Normalde controller içinde direkt Kafka’ya event gönderebilirdik:

```java
streamBridge.send("testEvent-out-0", event);
```

Ama bu risklidir.

Çünkü şu senaryolar olabilir:

- Kafka kapalı olabilir.
- Kafka’ya gönderim sırasında hata olabilir.
- Event gönderildi ama uygulama status bilgisini kaydedemeden patlayabilir.
- İşlem database’e yazıldı ama event Kafka’ya gönderilemedi.

Bu durumda sistemde tutarsızlık oluşur.

Outbox Pattern’in amacı:

> Kafka’ya gönderilecek event önce database’de kayıt altına alınır. Sonra başka bir mekanizma bu kayıtları okuyup Kafka’ya gönderir.

Yani event direkt Kafka’ya gitmez. Önce `outbox` tablosuna yazılır.

---

## 11. Outbox tablosunda ne tutuyoruz?

Ders notunda şöyle anlatıldı:

```text
Outbox -> XEvent, XTarihi, XTopic, XPayload
```

Yani outbox tablosunda genel olarak şu bilgiler tutulur:

| Alan | Anlamı |
|---|---|
| Event ID | Eventin benzersiz kimliği |
| Aggregate Type | Event hangi nesneyle ilgili? Örneğin Product |
| Aggregate ID | İlgili nesnenin ID’si |
| Event Type | Hangi event tipi? Örneğin testEvent |
| Payload | Kafka’ya gönderilecek JSON veri |
| Status | PENDING, SENT veya FAILED |
| Retry Count | Kaç kez denenmiş? |
| Error Message | Hata varsa hata mesajı |
| Created At | Ne zaman oluşturuldu? |
| Processed At | Ne zaman Kafka’ya gönderildi? |

---

## 12. OutboxStatus enum mantığı

Outbox eventin durumunu tutmak için enum kullanılır.

```java
public enum OutboxStatus {
    PENDING, // Event oluşturuldu ama henüz Kafka'ya gönderilmedi
    SENT,    // Event Kafka'ya başarıyla gönderildi
    FAILED   // Event birkaç kez denenmesine rağmen gönderilemedi
}
```

Ders notunda şöyle bir mantık vardı:

```text
OutboxStatus => 1,2,3
OutboxStatus => PENDING,SENT,FAILED
```

Yani enum database’e iki şekilde yazılabilir:

1. Sayı olarak: `0`, `1`, `2`
2. String olarak: `PENDING`, `SENT`, `FAILED`

Biz şunu kullandık:

```java
@Enumerated(EnumType.STRING)
```

Bu sayede database’de status alanı okunabilir şekilde görünür:

```text
PENDING
SENT
FAILED
```

Bu daha anlaşılırdır.

---

## 13. OutboxEvent entity kodu ve açıklaması

```java
@Entity // Bu sınıfın database tablosuna karşılık gelen bir JPA entity olduğunu söyler.
@Table(name = "outbox") // Database'de oluşacak tablonun adını "outbox" yapar.
public class OutboxEvent {

    @Id // Bu alanın primary key olduğunu belirtir.
    private UUID id; // Eventin benzersiz ID'si. Genelde eventId olarak kullanılır.

    private String aggregateType; // Eventin hangi ana nesneyle ilgili olduğunu belirtir. Örn: Product

    private String aggregateId; // İlgili nesnenin ID'si. Örn: productId

    private String eventType; // Eventin tipini belirtir. Örn: testEvent

    @Column(columnDefinition = "TEXT") // Payload uzun olabileceği için database tarafında TEXT olarak tutulur.
    private String payload; // Kafka'ya gönderilecek event içeriği. JSON formatında saklanır.

    private String errorMessage; // Event gönderilirken hata oluşursa hata mesajı burada tutulabilir.

    private int retryCount; // Event Kafka'ya gönderilirken kaç kez denendiğini tutar.

    private Instant createdAt; // Eventin outbox tablosuna ne zaman yazıldığını tutar.

    private Instant processedAt; // Eventin Kafka'ya ne zaman gönderildiğini veya işlendiğini tutar.

    @Enumerated(EnumType.STRING) // Enum değerini database'e sayı olarak değil, string olarak yazar.
    private OutboxStatus status; // Event durumu: PENDING, SENT veya FAILED
}
```

---

## 14. OutboxRepository

```java
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
}
```

Bu repository sayesinde `outbox` tablosuna şu işlemleri yapabiliriz:

- Kayıt ekleme
- Kayıt silme
- ID ile bulma
- Listeleme
- Güncelleme

`JpaRepository<OutboxEvent, UUID>` şu anlama gelir:

```text
Bu repository OutboxEvent entity'siyle çalışır ve primary key tipi UUID'dir.
```

---

## 15. TestEvent record yapısı

Event için record kullandık:

```java
public record TestEvent(UUID eventId, String message, UUID productId) {
}
```

Burada:

| Alan | Anlamı |
|---|---|
| `eventId` | Eventin benzersiz ID’si |
| `message` | Test için gönderilen mesaj |
| `productId` | Eventin ilişkili olduğu product ID |

`eventId` özellikle önemlidir. Çünkü consumer tarafında idempotency için bu ID kullanılacak.

---

## 16. Controller içinde neden StreamBridge kaldırıldı?

Önceden controller içinde direkt Kafka’ya gönderiyorduk:

```java
streamBridge.send("testEvent-out-0", event);
```

Ama Outbox Pattern’de controllerın görevi Kafka’ya göndermek değildir.

Controllerın görevi:

1. Event nesnesini oluşturmak
2. Eventi JSON’a çevirmek
3. Outbox tablosuna `PENDING` olarak kaydetmek

Kafka’ya gönderme işi daha sonra `OutboxPoller` tarafından yapılır.

Bu yüzden controllerdan `StreamBridge` kaldırıldı.

Özet:

```text
Eski yöntem:
Controller -> Kafka

Yeni yöntem:
Controller -> Outbox DB -> Poller -> Kafka
```

Bu sayede Kafka kapalı olsa bile request alınır ve event database’de bekler.

---

## 17. Controller kodu ve satır satır açıklaması

```java
@RequestMapping("/api/products") // Bu controller içindeki endpointlerin ana path'i /api/products olur.
@RestController // Bu sınıfın REST API controller olduğunu belirtir.
public class ProductsController {

    private final OutboxRepository outboxRepository; // Outbox tablosuna kayıt atmak için repository.
    private final ObjectMapper objectMapper; // Java nesnesini JSON string'e çevirmek için kullanılır.

    public ProductsController(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository; // Repository dependency injection ile alınır.
        this.objectMapper = objectMapper; // ObjectMapper dependency injection ile alınır.
    }

    @GetMapping // GET /api/products isteğini karşılar.
    public String test(@RequestParam String message) { // Query parametreden message alır. Örn: ?message=merhaba

        // ASLA!
        // Buradaki "ASLA" notu, normalde gerçek projede GET isteği ile veri değiştirilmemesi gerektiğini hatırlatır.
        // Ders için test amaçlı GET kullanılmıştır.

        UUID id = UUID.randomUUID(); // Test için bir productId oluşturuyoruz.
        UUID eventId = UUID.randomUUID(); // Her event için benzersiz bir eventId oluşturuyoruz.

        var event = new TestEvent(eventId, message, id); // Kafka'ya gidecek event nesnesini oluşturuyoruz.

        // streamBridge.send("testEvent-out-0", event);
        // Artık event direkt Kafka'ya gönderilmiyor.
        // Çünkü Outbox Pattern'de önce database'e yazıyoruz.

        // KAFKA'YA bir event gidecekse, önce kayıt altına alınacak.
        // Outbox -> XEvent, XTarihi, XTopic, XPayload

        OutboxEvent outboxEvent = new OutboxEvent(); // Database'e yazılacak outbox kaydı oluşturulur.

        outboxEvent.setId(eventId); // Outbox kaydının ID'si eventId ile aynı yapıldı.

        outboxEvent.setAggregateType("Product"); // Bu event Product aggregate'ı ile ilgili.

        outboxEvent.setAggregateId(id.toString()); // İlgili productId string olarak kaydedilir.

        outboxEvent.setEventType("testEvent"); // Event tipi testEvent olarak kaydedilir.
        // Poller daha sonra bunu kullanarak binding adını oluşturacak: testEvent-out-0

        outboxEvent.setPayload(toJson(event)); // Event nesnesi JSON string'e çevrilip payload alanına yazılır.

        outboxEvent.setStatus(OutboxStatus.PENDING); // Event henüz Kafka'ya gitmediği için PENDING durumunda başlar.

        outboxEvent.setCreatedAt(Instant.now()); // Eventin outbox tablosuna yazıldığı zaman kaydedilir.

        outboxRepository.save(outboxEvent); // Outbox kaydı database'e yazılır.

        return "Başarılı"; // İstek başarıyla alındı ve event outbox'a kaydedildi.
    }

    private String toJson(Object o) { // Herhangi bir Java nesnesini JSON string'e çeviren yardımcı metot.
        try {
            return objectMapper.writeValueAsString(o); // ObjectMapper ile nesne JSON'a çevrilir.
        } catch (Exception e) {
            throw new RuntimeException(e); // JSON'a çevirme başarısız olursa runtime hata fırlatılır.
        }
    }
}
```

---

## 18. Neden JSON metodu yazdık?

Outbox tablosundaki `payload` alanı `String` olarak tutuluyor.

Ama bizim eventimiz Java nesnesi:

```java
var event = new TestEvent(eventId, message, id);
```

Bu nesneyi database’e doğrudan yazmak yerine JSON olarak saklıyoruz.

Örnek JSON:

```json
{
  "eventId": "...",
  "message": "merhaba",
  "productId": "..."
}
```

Bu yüzden şu metodu yazdık:

```java
private String toJson(Object o) {
    try {
        return objectMapper.writeValueAsString(o);
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}
```

Bu metodun amacı:

> Java nesnesini JSON string’e çevirmek.

---

## 19. Postman ile test

Postman’den GET isteği attık:

```text
GET localhost:8082/api/products?message=bqwrbqwrvqwvq
```

Not: Ders notunda `/apl/products` yazılmış ama doğru path controller’a göre şu olmalı:

```text
/api/products
```

İstek atılınca controller Kafka’ya direkt event göndermedi. Bunun yerine `outbox` tablosuna yeni bir kayıt attı.

pgAdmin’den bakınca `outbox` tablosunda event kaydı görüldü.

---

# Polling Mekanizması

## 20. Outbox kaydını Kafka’ya kim gönderecek?

Controller sadece outbox tablosuna kayıt atıyor.

Şimdi soru şu:

> Bu kayıtları okuyup Kafka’ya gönderecek mekanizma nerede?

Bu mekanizmaya derste **poller** dedik.

Poller:

- Belirli aralıklarla çalışır.
- Database’de gönderilmeyi bekleyen event var mı diye bakar.
- `PENDING` durumundaki eventleri alır.
- Kafka’ya gönderir.
- Başarılı olursa status değerini `SENT` yapar.
- Hata olursa retry count artırır.
- Çok fazla hata olursa `FAILED` yapar.

---

## 21. Polling nedir?

Polling, sistemin belirli aralıklarla bir yeri kontrol etmesidir.

Bizim örnekte:

```text
Her 20 saniyede bir outbox tablosuna bak.
Gönderilecek event var mı?
Varsa Kafka'ya gönder.
```

Polling başlangıç için kolay bir çözümdür ama profesyonel sistemlerde her zaman en iyi çözüm olmayabilir.

Çünkü sürekli database’e query atılır.

Örneğin her 20 saniyede bir:

```sql
SELECT * FROM outbox WHERE status = 'PENDING'
```

çalıştırmak yoğun sistemlerde performans yükü oluşturabilir.

---

## 22. CDC ve Debezium nedir?

Polling yerine daha profesyonel yaklaşım **CDC** kullanmaktır.

CDC açılımı:

```text
Change Data Capture
```

Yani:

> Veritabanındaki değişiklikleri yakalama mekanizmasıdır.

Polling’de sistem sürekli şunu sorar:

```text
Database değişti mi?
Database değişti mi?
Database değişti mi?
```

CDC’de ise database değişince sistem bundan haberdar olur.

Debezium da bir CDC aracıdır.

Debezium mantığı:

- Database loglarını dinler.
- Yeni kayıt, update veya delete olduğunda bunu yakalar.
- Bu değişikliği Kafka’ya aktarabilir.

Bu yüzden derste ödev olarak şu verildi:

```text
Burayı CDC ile, yani Debezium ile değiştir.
```

Biz başlangıçta daha anlaşılır olduğu için polling yaptık.

---

## 23. Repository içine özel query yazma

Poller’ın göndereceği eventleri bulması gerekiyor.

Ama standart `JpaRepository` içinde şu işi yapan hazır bir metot yok:

```text
status = PENDING ve retryCount < 3 olan ilk 10 event'i getir.
```

Bu yüzden repository içine özel query yazdık.

```java
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            Select * from outbox
            Where status = 'PENDING' and retry_count < 3
            ORDER BY created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<OutboxEvent> findPublishable(@Param("limit") int limit);
}
```

Satır satır açıklaması:

```java
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    // OutboxEvent entity'si için repository oluşturuyoruz.
    // UUID ise entity'nin primary key tipidir.

    @Query(value = """
            Select * from outbox
            Where status = 'PENDING' and retry_count < 3
            ORDER BY created_at
            LIMIT :limit
            """, nativeQuery = true)
    // @Query ile kendi SQL sorgumuzu yazıyoruz.
    // nativeQuery = true olduğu için bu JPQL değil, direkt SQL sorgusudur.
    // status = 'PENDING' olan kayıtları alır.
    // retry_count < 3 şartı ile çok fazla denenmemiş kayıtları seçer.
    // ORDER BY created_at ile eski kayıtlar önce gönderilir.
    // LIMIT :limit ile kaç tane kayıt alınacağını dışarıdan parametreyle belirleriz.

    List<OutboxEvent> findPublishable(@Param("limit") int limit);
    // Bu metot gönderilmeye uygun eventleri liste olarak döndürür.
    // Örneğin findPublishable(10) dersek en fazla 10 event getirir.
}
```

---

## 24. OutboxPoller kodu ve açıklaması

`polling` adında bir klasör açtık ve içine `OutboxPoller` classını yazdık.

```java
@Component // Bu sınıfı Spring Bean haline getirir. Spring bu sınıfı yönetir.
public class OutboxPoller {

    private final OutboxRepository outboxRepository; // Outbox tablosundan event okumak ve güncellemek için kullanılır.
    private final StreamBridge streamBridge; // Eventi Kafka'ya göndermek için kullanılır.

    public OutboxPoller(OutboxRepository outboxRepository, StreamBridge streamBridge) {
        this.outboxRepository = outboxRepository; // Repository dependency injection ile alınır.
        this.streamBridge = streamBridge; // StreamBridge dependency injection ile alınır.
    }

    @Scheduled(fixedDelay = 20000) // Bu metot her çalışması bittikten 20 saniye sonra tekrar çalışır.
    @Transactional // Bu metot içindeki database işlemlerini transaction içinde çalıştırır.
    public void publishPendingEvents() {

        List<OutboxEvent> events = outboxRepository.findPublishable(10); // Gönderilmeye uygun en fazla 10 event alınır.

        for (OutboxEvent event : events) { // Her event tek tek işlenir.
            try {
                streamBridge.send(event.getEventType() + "-out-0", event.getPayload());
                // Event Kafka'ya gönderilir.
                // eventType testEvent ise binding adı testEvent-out-0 olur.
                // Payload JSON string olarak gönderilir.

                event.setStatus(OutboxStatus.SENT);
                // Kafka'ya gönderim başarılıysa eventin durumu SENT yapılır.

            } catch (Exception e) {
                // Kafka'ya gönderirken hata olursa burası çalışır.

                if (event.getRetryCount() >= 3) {
                    event.setStatus(OutboxStatus.FAILED);
                    // Retry sayısı 3 veya daha fazlaysa artık FAILED yapılır.
                } else {
                    event.setRetryCount(event.getRetryCount() + 1);
                    // Henüz 3 denemeye ulaşmadıysa retryCount 1 artırılır.
                }
            }

            event.setProcessedAt(Instant.now());
            // Bu event için en son işlem yapılan zaman kaydedilir.

            outboxRepository.save(event);
            // Eventin güncel durumu database'e kaydedilir.
        }
    }
}
```

---

## 25. @Scheduled ne işe yarar?

```java
@Scheduled(fixedDelay = 20000)
```

Bu anotasyon metodu belirli aralıklarla çalıştırır.

`fixedDelay = 20000` şu demektir:

```text
Metot çalışsın.
Metot tamamen bitsin.
20 saniye bekle.
Sonra tekrar çalışsın.
```

Yani poller her 20 saniyede bir outbox tablosunu kontrol eder.

---

## 26. @EnableScheduling neden gerekli?

`@Scheduled` anotasyonunun çalışması için Spring Boot uygulamasında scheduling aktif edilmelidir.

Bu yüzden main class’a `@EnableScheduling` ekledik.

```java
@SpringBootApplication // Spring Boot uygulamasının ana sınıfı olduğunu belirtir.
@EnableScheduling // @Scheduled anotasyonlu metotların çalışmasını aktif eder.
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args); // Uygulamayı başlatır.
    }
}
```

Eğer `@EnableScheduling` eklenmezse `OutboxPoller` içindeki `@Scheduled` metodu çalışmaz.

---

## 27. @Transactional burada neden önemli?

`@Transactional`, metot içindeki database işlemlerini tek bir transaction olarak çalıştırır.

Örneğin:

1. Event Kafka’ya gönderildi.
2. Status `SENT` yapılacak.
3. Ama database save sırasında hata oldu.

Bu durumda sistemin tutarsız hale gelmesini istemeyiz.

`@Transactional` şu mantığa yardımcı olur:

> İşlemler bir bütün olarak başarılı olsun. Hata olursa database tarafındaki değişiklikler geri alınsın.

Ama önemli bir not: Kafka’ya gönderim dış sistem çağrısıdır. Database transaction ile Kafka gönderimini tamamen atomik hale getirmek kolay değildir. Outbox Pattern zaten bu problemi azaltmak için kullanılır.

---

## 28. Kafka kapalıyken sistem nasıl davranır?

Outbox Pattern’in en güzel tarafı burasıdır.

Kafka kapalı olsa bile:

- `product-service` request almaya devam eder.
- Event direkt Kafka’ya gönderilmez.
- Event `outbox` tablosuna `PENDING` olarak kaydedilir.
- Kafka açılınca poller tekrar dener.
- Başarılı olursa event Kafka’ya gönderilir ve status `SENT` olur.

Yani Kafka kapalıyken bile ne göndereceğimizi kaybetmeyiz.

Örnek senaryo:

```text
Kafka 3 saat kapalı kaldı.
Bu sırada 200 event oluştu.
Bu 200 event outbox tablosunda PENDING kaldı.
Kafka tekrar açılınca poller bunları sırayla göndermeye başladı.
```

Bu sayede event kaybı olmaz.

---

## 29. Retry count ve FAILED kayıtları neden var?

Kafka kapalıysa ya da event gönderilemiyorsa sürekli denemek mantıklı değildir.

Bu yüzden `retryCount` tutuyoruz.

Mantık:

```text
Event gönderilemezse retryCount artır.
3 defadan fazla başarısız olursa FAILED yap.
```

Böylece sorunlu event sonsuza kadar denenmez.

Ama sonradan manuel müdahale yapılabilir.

Örneğin Kafka 3 saat çöktü ve bazı eventler `FAILED` oldu.  
Kafka düzeldikten sonra pgAdmin’den bu eventlerin durumunu tekrar `PENDING` yapabiliriz veya retry count değerini azaltabiliriz.

---

## 30. Aynı anda çok fazla event olursa performans sorunu olur mu?

Evet, olabilir.

Her event için database’e yazmak ek bir maliyettir. Ama Outbox Pattern bilinçli olarak bunu yapar. Çünkü amaç event kaybını önlemektir.

Performans için batch mantığı kullanılır.

Örneğin:

```java
findPublishable(10)
```

Bu şu demektir:

```text
Her çalışmada en fazla 10 event al ve işle.
```

Daha büyük sistemlerde bu sayı 100 veya 500 olabilir.

Ama tüm eventleri aynı anda güncellemek risklidir.

Örneğin 100 event aldık. 99 tanesi sağlam, 1 tanesi bozuk.

Eğer hepsini tek işlem gibi düşünürsek bir bozuk mesaj tüm batch’i bozabilir.

Bu yüzden tek tek işlemek daha güvenli olabilir.

Ders notundaki mantık:

> 10’ar 10’ar veya 100’er 100’er almak mantıklı. Ama hepsini tek update ile güncellemek yerine tek tek güncellemek daha güvenli olabilir.

---

## 31. Restoran fişi örneği doğru mu?

Evet, yaklaşım doğru.

Örnek şöyle düşünülebilir:

Bir restoranda sipariş veriliyor.

Normalde sipariş hem kasaya hem mutfağa gitmeli.

Ama mutfak sistemi geçici olarak kapalıysa çalışan siparişi çöpe atmıyor. Önce sipariş fişini güvenli bir yerde saklıyor.

Sonra mutfak hazır olduğunda bekleyen fişleri sırayla mutfağa iletiyor.

Bu örnekte:

| Restoran örneği | Yazılım karşılığı |
|---|---|
| Sipariş | Event |
| Fişin saklanması | Outbox tablosuna kayıt |
| Mutfak | Kafka / consumer tarafı |
| Fişleri götüren çalışan | Poller |
| Mutfak kapalıyken fişleri bekletmek | Kafka kapalıyken PENDING eventleri saklamak |

Bu örnek Outbox Pattern’i anlatmak için uygundur.

Ama teknik olarak şunu da bilmek gerekir:

- Outbox, eventin kaybolmamasını sağlar.
- Eventin gerçekten sadece bir kez işlenmesini garanti etmek için consumer tarafında idempotency gerekir.

Bu yüzden ikinci konu olarak Inbox Pattern işlendi.

---

## 32. Outbox tablosu şişerse ne yapmalıyız?

Evet, outbox tablosu zamanla büyür. Çünkü her event önce buraya yazılır.

Bu yüzden gerçek sistemlerde outbox tablosu temizlenir veya arşivlenir.

Genel yaklaşım:

- `PENDING` kayıtlar silinmez.
- `FAILED` kayıtlar hemen silinmez, incelenir.
- `SENT` kayıtlar belli bir süre sonra arşivlenebilir veya silinebilir.

Örnek strateji:

| Status | Ne yapılmalı? |
|---|---|
| `PENDING` | Silinmemeli, Kafka’ya gönderilmeyi bekliyor |
| `FAILED` | Önce hata incelenmeli, gerekirse tekrar PENDING yapılmalı |
| `SENT` | Belli süre sonra arşivlenebilir veya silinebilir |

Temizleme sıklığı projeye göre değişir.

Örnek:

- Her gece `SENT` kayıtları arşivle.
- 7 günden eski `SENT` kayıtları başka tabloya taşı.
- 30 günden eski `SENT` kayıtları sil.
- `FAILED` kayıtları manuel kontrol için tut.

Basit örnek SQL:

```sql
DELETE FROM outbox
WHERE status = 'SENT'
AND created_at < now() - interval '30 days';
```

Ama gerçek projede direkt silmek yerine önce arşivlemek daha güvenli olabilir.

---

# Inbox Pattern ve Idempotency

## 33. Idempotency nedir?

Idempotency şu demektir:

> Aynı işlem birden fazla kez çalışsa bile sonucu değiştirmemesi gerekir.

Kafka sistemlerinde aynı event bazen birden fazla kez consumer’a gelebilir.

Örneğin:

- Consumer eventi işledi.
- Ama offset commit edilemeden uygulama kapandı.
- Kafka aynı eventi tekrar gönderdi.

Bu durumda consumer aynı event için tekrar işlem yaparsa sorun oluşabilir.

Örneğin:

- Aynı bildirim iki kez gider.
- Aynı ödeme iki kez işlenir.
- Aynı kayıt iki kez oluşturulur.

Bunu engellemek için eventin `eventId` alanını kullanırız.

---

## 34. Inbox Pattern nedir?

Producer tarafında Outbox vardı.

Consumer tarafında ise Inbox mantığı vardır.

Biz derste tablo adı olarak `processed_events` kullandık.

Mantık:

```text
Bu eventId daha önce işlendi mi?
Evet -> tekrar işleme.
Hayır -> işle ve processed_events tablosuna kaydet.
```

Yani consumer tarafında şunu tutuyoruz:

```text
Ben bu event ID'yi daha önce işledim mi?
```

Bu sayede aynı event ikinci kez gelirse consumer bunu fark eder.

---

## 35. Consumer event modelinde eventId neden gerekli?

Event sınıfımız şöyleydi:

```java
public record TestEvent(UUID eventId, String message, UUID productId) {
}
```

Buradaki `eventId` consumer tarafında çok önemlidir.

Çünkü `user-service` bu ID’ye bakarak şunu anlayacak:

```text
Bu event daha önce işlendi mi?
```

Eğer eventte ID olmazsa aynı mesajın daha önce gelip gelmediğini güvenilir şekilde anlayamayız.

---

## 36. ProcessedEvent entity kodu ve açıklaması

`user-service` tarafında `ProcessedEvent` entity’si açtık.

```java
package com.turkcell.user_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity // Bu sınıfın database tablosuna karşılık geldiğini belirtir.
@Table(name = "processed_events") // Database'deki tablo adını processed_events yapar.
public class ProcessedEvent {

    @Id // eventId primary key olur. Aynı eventId ikinci kez kaydedilemez.
    private UUID eventId; // İşlenen eventin benzersiz ID'si.

    private Instant processedAt; // Bu eventin ne zaman işlendiğini tutar.

    public UUID getEventId() {
        return eventId; // eventId değerini döndürür.
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId; // eventId değerini set eder.
    }

    public Instant getProcessedAt() {
        return processedAt; // Eventin işlenme zamanını döndürür.
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt; // Eventin işlenme zamanını set eder.
    }
}
```

Bu tabloda iki alan yeterli oldu:

| Alan | Anlamı |
|---|---|
| `eventId` | İşlenen eventin ID’si |
| `processedAt` | Ne zaman işlendiği |

---

## 37. ProcessedEventRepository

```java
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
```

Bu repository ile `processed_events` tablosunda şu işlemleri yapabiliriz:

- Event ID var mı kontrol etme
- Yeni işlenen eventi kaydetme
- ID ile kayıt bulma

En çok kullandığımız metot:

```java
processedEventRepository.findById(event.eventId())
```

Bu metot şunu sorar:

```text
Bu eventId processed_events tablosunda var mı?
```

---

## 38. Consumer kodu ve satır satır açıklaması

```java
package com.turkcell.user_service.consumer;

import java.time.Instant;
import java.util.function.Consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.turkcell.user_service.entity.ProcessedEvent;
import com.turkcell.user_service.event.TestEvent;
import com.turkcell.user_service.repository.ProcessedEventRepository;

@Configuration // Bu sınıfın Spring configuration sınıfı olduğunu belirtir.
public class TestEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    // Daha önce işlenen eventleri kontrol etmek ve yeni işlenen eventleri kaydetmek için kullanılır.

    public TestEventConsumer(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
        // Repository dependency injection ile alınır.
    }

    @Bean // Bu metodu Spring Bean olarak kaydeder. Spring Cloud Stream bu Consumer'ı Kafka consumer olarak kullanır.
    public Consumer<TestEvent> consumeTestEvent() {
        return event -> {
            // Kafka'dan bir TestEvent geldiğinde bu lambda çalışır.

            var processedEvent = processedEventRepository.findById(event.eventId()).orElse(null);
            // Gelen eventin eventId'si processed_events tablosunda var mı diye kontrol edilir.
            // Eğer varsa bu event daha önce işlenmiştir.
            // Eğer yoksa null döner ve event ilk kez işleniyor demektir.

            if (processedEvent != null) {
                // Eğer processedEvent null değilse bu event daha önce işlenmiştir.
                System.out.println("Bu event zaten işlendi: " + event.eventId());
                // Log basılır.
                return;
                // Metottan çıkılır. Event tekrar işlenmez.
            }

            System.out.println(
                "TestEvent İŞLENDİ: " + event.message()
                + ", Product ID: " + event.productId()
                + ", Event ID: " + event.eventId()
            );
            // Event ilk kez gelmişse burada asıl business işlem yapılır.
            // Şu an derste sadece ekrana yazdırıyoruz.
            // Gerçek projede burada kullanıcıya bildirim gönderme, kayıt oluşturma vb. yapılabilir.

            processedEvent = new ProcessedEvent();
            // Bu eventin işlendiğini kaydetmek için yeni ProcessedEvent nesnesi oluşturulur.

            processedEvent.setEventId(event.eventId());
            // İşlenen eventin ID'si tabloya yazılır.

            processedEvent.setProcessedAt(Instant.now());
            // Eventin işlenme zamanı kaydedilir.

            processedEventRepository.save(processedEvent);
            // Artık bu event processed_events tablosuna kaydedildi.
            // Aynı event tekrar gelirse findById dolu dönecek ve tekrar işlenmeyecek.
        };
    }
}
```

---

## 39. Consumer tarafındaki akış

Kafka’dan event geldiğinde `user-service` şu adımları izler:

1. Eventin `eventId` değerini alır.
2. `processed_events` tablosunda bu ID var mı diye bakar.
3. Varsa:
   - “Bu event zaten işlendi” der.
   - İşlemi tekrar yapmaz.
4. Yoksa:
   - Eventi işler.
   - `processed_events` tablosuna event ID’yi kaydeder.

Bu sayede aynı event tekrar gelse bile ikinci kez işlenmez.

---

## 40. Outbox ve Inbox birlikte nasıl çalışır?

Genel akış şu şekildedir:

```text
1. Client product-service'e istek atar.
2. product-service TestEvent oluşturur.
3. Event Kafka'ya direkt gönderilmez.
4. Event outbox tablosuna PENDING olarak yazılır.
5. OutboxPoller belirli aralıklarla PENDING eventleri okur.
6. Poller eventi Kafka'ya gönderir.
7. Gönderim başarılıysa outbox status SENT olur.
8. user-service Kafka'dan eventi tüketir.
9. user-service eventId daha önce işlendi mi diye processed_events tablosuna bakar.
10. İşlenmediyse eventi işler ve processed_events tablosuna kaydeder.
11. Aynı event tekrar gelirse user-service tekrar işlemez.
```

---

## 41. Kısa özet

Bu derste öğrendiğimiz ana fikir:

```text
Producer tarafında event kaybolmasın diye Outbox Pattern kullanılır.
Consumer tarafında aynı event iki kez işlenmesin diye Inbox / Idempotency kullanılır.
```

Daha kısa hali:

```text
Outbox: Göndermeden önce kaydet.
Inbox: İşlemeden önce daha önce işlendi mi kontrol et.
```

---

## 42. Dersteki önemli cümleler

- Kafka’ya bir event gidecekse önce kayıt altına alınmalı.
- Controller artık direkt Kafka’ya göndermemeli.
- Event önce outbox tablosuna yazılmalı.
- Poller belirli aralıklarla outbox tablosunu kontrol eder.
- Kafka kapalı olsa bile eventler kaybolmaz.
- Kafka açılınca bekleyen eventler gönderilir.
- Consumer tarafında eventId ile daha önce işlenip işlenmediği kontrol edilir.
- Aynı event tekrar gelirse tekrar işlenmez.
- Polling başlangıç için kolaydır ama profesyonel çözüm CDC / Debezium olabilir.

---

## 43. Ödev

Derste verilen ödev:

```text
Outbox polling mekanizmasını CDC ile, yani Debezium ile değiştir.
```

Bu ödevde amaç:

- Poller ile sürekli database’e sormak yerine,
- Database değişikliğini Debezium ile yakalamak,
- Outbox eventlerini Kafka’ya daha profesyonel şekilde aktarmaktır.

---

## 44. Commit mesajı önerisi

Bu ders için uygun İngilizce commit mesajı:

```text
feat: implement transactional outbox and idempotent event consumer
```

Daha kısa alternatif:

```text
feat: add outbox pattern and inbox idempotency
```


