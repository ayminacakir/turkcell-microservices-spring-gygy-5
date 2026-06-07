# Outbox Polling Mekanizmasını Debezium CDC ile Değiştirme

Bu doküman, mikroservisler arasında event iletişimini daha profesyonel hale getirmek için yapılan **Transactional Outbox + CDC + Debezium + Kafka** çalışmasını anlatır.

Ödevin amacı:

> Poller ile sürekli database'e sormak yerine, database değişikliğini Debezium ile yakalamak ve outbox eventlerini Kafka'ya aktarmak.

---

## 1. Eski Yapı: Outbox Polling

İlk yapıda Product Service içinde bir `OutboxPoller` sınıfı vardı. Bu sınıf belirli aralıklarla veritabanına gidip `PENDING` durumundaki eventleri arıyordu.

Eski akış:

```text
Product Service
  |
  | event oluşturur
  v
outbox tablosu
  |
  | OutboxPoller her 20 saniyede bir tabloyu sorgular
  v
Kafka test-topic
  |
  | User Service dinler
  v
processed_events tablosu
```

Eski poller mantığı:

```java
@Component
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final StreamBridge streamBridge;

    public OutboxPoller(OutboxRepository outboxRepository, StreamBridge streamBridge) {
        this.outboxRepository = outboxRepository;
        this.streamBridge = streamBridge;
    }

    @Scheduled(fixedDelay = 20000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findPublishable(10);

        for (OutboxEvent event : events) {
            try {
                streamBridge.send(event.getEventType() + "-out-0", event.getPayload());
                event.setStatus(OutboxStatus.SENT);
            } catch (Exception e) {
                if (event.getRetryCount() >= 3) {
                    event.setStatus(OutboxStatus.FAILED);
                } else {
                    event.setRetryCount(event.getRetryCount() + 1);
                }
            }

            event.setProcessedAt(Instant.now());
            outboxRepository.save(event);
        }
    }
}
```

Bu yapı çalışır ama sürekli veritabanına sorgu attığı için daha maliyetlidir. Ayrıca event yayınlama sorumluluğu Product Service içinde kalır. Bu yüzden ödevde polling yerine Debezium CDC yapısına geçildi.

---

## 2. Yeni Yapı: Debezium CDC

Yeni yapıda Product Service sadece outbox tablosuna event kaydı atar. Kafka'ya gönderme işini Debezium yapar.

Yeni akış:

```text
Product Service
  |
  | outbox tablosuna INSERT
  v
PostgreSQL outbox tablosu
  |
  | Debezium WAL / CDC ile değişikliği yakalar
  v
Kafka test-topic
  |
  | User Service consumer dinler
  v
processed_events tablosu
```

Kısaca:

```text
Eski yapı: Uygulama DB'yi sürekli sorguluyordu.
Yeni yapı: Debezium DB değişikliğini otomatik yakalıyor.
```

CDC, **Change Data Capture** demektir. Veritabanında oluşan INSERT, UPDATE, DELETE gibi değişikliklerin yakalanmasıdır. Bizim senaryoda Debezium, `outbox` tablosuna gelen INSERT kaydını yakalar ve Kafka'ya event olarak gönderir.

---

## 3. Product Service Tarafı

### 3.1 TestEvent

```java
package com.turkcell.product_service.event;

import java.util.UUID;

public record TestEvent(UUID eventId, String message, UUID productId) {
}
```

Burada:

- `eventId`: Event'in benzersiz kimliği.
- `message`: Test mesajı.
- `productId`: Ürün kimliği.

`eventId`, User Service tarafında idempotency kontrolü için kullanılır.

---

### 3.2 OutboxStatus

```java
package com.turkcell.product_service.entity;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
```

Polling yapısında `SENT` ve `FAILED` durumları uygulama tarafından güncelleniyordu. Debezium yapısında ise `PENDING` kalması normaldir çünkü Debezium tabloyu otomatik güncellemez, sadece değişikliği Kafka'ya aktarır.

---

### 3.3 OutboxEvent Entity

```java
package com.turkcell.product_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox")
public class OutboxEvent {

    @Id
    private UUID id;

    private String aggregateType;
    private String aggregateId;
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    private String errorMessage;
    private int retryCount;

    private Instant createdAt;
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public void setStatus(OutboxStatus status) {
        this.status = status;
    }
}
```

Önemli alanlar:

```text
id             -> eventId
aggregateType  -> Product
aggregateId    -> productId
eventType      -> testEvent
payload        -> JSON event içeriği
status         -> PENDING
createdAt      -> kayıt zamanı
```

---

### 3.4 Payload Neden TEXT?

İlk denemede `payload` alanı `jsonb` yapılmıştı:

```java
@Column(columnDefinition = "jsonb")
private String payload;
```

Ama Java tarafında payload `String` olarak gönderildiği için PostgreSQL şu hatayı verdi:

```text
column "payload" is of type jsonb but expression is of type character varying
```

Bu yüzden daha kolay ve uyumlu çözüm olarak `payload` alanı `TEXT` yapıldı:

```java
@Column(columnDefinition = "TEXT")
private String payload;
```

Veritabanında kolon tipi dönüşümü için kullanılan SQL:

```sql
ALTER TABLE outbox
ALTER COLUMN payload TYPE TEXT
USING payload::text;
```

---

### 3.5 OutboxRepository

```java
package com.turkcell.product_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.product_service.entity.OutboxEvent;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
}
```

Polling dönemindeki özel sorgu artık gerekli değildir. Çünkü Debezium veritabanındaki değişikliği kendisi yakalar.

Eski polling sorgusu:

```java
@Query(value = """
        Select * from outbox
        Where status = 'PENDING' and retry_count < 3
        ORDER BY created_at
        LIMIT :limit
        """, nativeQuery = true)
List<OutboxEvent> findPublishable(@Param("limit") int limit);
```

Debezium yapısında bu sorguya ihtiyaç yoktur.

---

### 3.6 ProductsController

```java
package com.turkcell.product_service.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.product_service.entity.OutboxEvent;
import com.turkcell.product_service.entity.OutboxStatus;
import com.turkcell.product_service.entity.TestClass;
import com.turkcell.product_service.event.TestEvent;
import com.turkcell.product_service.repository.OutboxRepository;

@RequestMapping("/api/products")
@RestController
public class ProductsController {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ProductsController(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/test")
    public TestClass test2() {
        return new TestClass("Product Service Test Başarılı");
    }

    @GetMapping
    public String test(@RequestParam String message) {
        UUID productId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        var event = new TestEvent(eventId, message, productId);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(eventId);
        outboxEvent.setAggregateType("Product");
        outboxEvent.setAggregateId(productId.toString());
        outboxEvent.setEventType("testEvent");
        outboxEvent.setPayload(toJson(event));
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setCreatedAt(Instant.now());

        outboxRepository.save(outboxEvent);

        return "Başarılı";
    }

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
```

Bu controller artık Kafka'ya direkt mesaj göndermez. Eski yaklaşım şuydu:

```java
streamBridge.send("testEvent-out-0", event);
```

Yeni yaklaşım:

```java
outboxRepository.save(outboxEvent);
```

Bunun sebebi, Kafka'ya event gönderme işini artık Debezium'un üstlenmesidir.

---

### 3.7 TestClass

```java
package com.turkcell.product_service.entity;

public record TestClass(String message) {
}
```

---

### 3.8 ProductServiceApplication

Polling kullanılmadığı için `@EnableScheduling` zorunlu değildir. `OutboxPoller` silindiyse ya da `@Component` kapatıldıysa scheduling aktif olsa bile polling çalışmaz.

```java
package com.turkcell.product_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
```

---

## 4. User Service Tarafı

User Service, Kafka'dan gelen eventi dinler. Aynı event tekrar gelirse yeniden işlememek için `processed_events` tablosunu kullanır. Bu yaklaşıma **idempotency** denir.

### 4.1 User Service TestEvent

```java
package com.turkcell.user_service.event;

import java.util.UUID;

public record TestEvent(UUID eventId, String message, UUID productId) {
}
```

Product Service ve User Service tarafındaki event alanlarının aynı olması gerekir.

---

### 4.2 ProcessedEvent Entity

```java
package com.turkcell.user_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    private UUID eventId;

    private Instant processedAt;

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
```

Bu tablo sayesinde aynı event tekrar gelirse tekrar işlenmez.

---

### 4.3 ProcessedEventRepository

```java
package com.turkcell.user_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.user_service.entity.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
```

---

### 4.4 TestEventConsumer

```java
package com.turkcell.user_service.consumer;

import java.time.Instant;
import java.util.function.Consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.turkcell.user_service.entity.ProcessedEvent;
import com.turkcell.user_service.event.TestEvent;
import com.turkcell.user_service.repository.ProcessedEventRepository;

@Configuration
public class TestEventConsumer {

    private final ProcessedEventRepository processedEventRepository;

    public TestEventConsumer(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @Bean
    public Consumer<TestEvent> consumeTestEvent() {
        return event -> {
            var processedEvent = processedEventRepository.findById(event.eventId()).orElse(null);

            if (processedEvent != null) {
                System.out.println("Bu event zaten işlendi: " + event.eventId());
                return;
            }

            System.out.println(
                    "TestEvent İŞLENDİ: "
                            + event.message()
                            + ", Product ID: "
                            + event.productId()
                            + ", Event ID: "
                            + event.eventId()
            );

            processedEvent = new ProcessedEvent();
            processedEvent.setEventId(event.eventId());
            processedEvent.setProcessedAt(Instant.now());

            processedEventRepository.save(processedEvent);
        };
    }
}
```

Mantık:

```text
1. Event geldi.
2. eventId processed_events tablosunda var mı kontrol edildi.
3. Varsa tekrar işlenmedi.
4. Yoksa event işlendi.
5. processed_events tablosuna kayıt atıldı.
```

---

### 4.5 UsersController

```java
package com.turkcell.user_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/users")
@RestController
public class UsersController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello user service";
    }

    @GetMapping
    public String get() {
        System.out.println("UsersController çalıştı");
        return "UsersController";
    }
}
```

---

## 5. Docker Compose Yapısı

Debezium için Docker tarafında iki önemli değişiklik yapıldı:

```text
1. product-db için wal_level=logical açıldı.
2. debezium-connect servisi eklendi.
```

### 5.1 Kafka

Projede Kafka KRaft mode ile çalışıyor. Zookeeper kullanılmıyor.

```yaml
kafka:
  image: apache/kafka:4.2.0
  container_name: kafka
  ports:
    - "9092:9092"
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093

    KAFKA_LISTENERS: INTERNAL://0.0.0.0:19092,EXTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
    KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:19092,EXTERNAL://localhost:9092
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
    KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
    KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER

    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
    KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1

    KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    KAFKA_NUM_PARTITIONS: 3
  volumes:
    - kafka-data:/var/lib/kafka/data
  healthcheck:
    test: ["CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 || exit 1"]
    interval: 10s
    timeout: 10s
    retries: 10
  networks:
    - kafka-net
```

Önemli adresler:

```text
localhost:9092 -> Host makineden Kafka'ya bağlanmak için
kafka:19092   -> Container içinden Kafka'ya bağlanmak için
```

Debezium container içinde çalıştığı için `kafka:19092` kullanılır.

---

### 5.2 Debezium Connect

```yaml
debezium-connect:
  image: debezium/connect:2.6
  container_name: debezium-connect
  ports:
    - "8083:8083"
  environment:
    BOOTSTRAP_SERVERS: kafka:19092
    GROUP_ID: debezium-connect-group
    CONFIG_STORAGE_TOPIC: debezium_connect_configs
    OFFSET_STORAGE_TOPIC: debezium_connect_offsets
    STATUS_STORAGE_TOPIC: debezium_connect_statuses
    CONFIG_STORAGE_REPLICATION_FACTOR: 1
    OFFSET_STORAGE_REPLICATION_FACTOR: 1
    STATUS_STORAGE_REPLICATION_FACTOR: 1
    KEY_CONVERTER: org.apache.kafka.connect.storage.StringConverter
    VALUE_CONVERTER: org.apache.kafka.connect.json.JsonConverter
    VALUE_CONVERTER_SCHEMAS_ENABLE: "false"
  depends_on:
    kafka:
      condition: service_healthy
    product-db:
      condition: service_started
  networks:
    - kafka-net
    - default
```

Debezium iki yere erişmelidir:

```text
Kafka      -> kafka-net
product-db -> default network
```

Bu yüzden Debezium iki network'e bağlandı.

---

### 5.3 Product DB

```yaml
product-db:
  image: postgres:17
  container_name: product-db
  environment:
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: test12345
    POSTGRES_DB: products
  ports:
    - "5433:5432"
  command:
    - "postgres"
    - "-c"
    - "wal_level=logical"
    - "-c"
    - "max_wal_senders=10"
    - "-c"
    - "max_replication_slots=10"
  volumes:
    - product-db-data:/var/lib/postgresql/data
```

En önemli ayar:

```yaml
wal_level=logical
```

Çünkü Debezium PostgreSQL değişikliklerini WAL üzerinden okuyacaktır.

Kontrol komutu:

```bash
docker exec -it product-db psql -U postgres -d products
```

```sql
SHOW wal_level;
```

Beklenen çıktı:

```text
logical
```

---

### 5.4 User DB

```yaml
user-db:
  image: postgres:17
  container_name: user-db
  environment:
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: test12345
    POSTGRES_DB: users
  ports:
    - "5434:5432"
  volumes:
    - user-db-data:/var/lib/postgresql/data
```

---

### 5.5 Kafka UI

```yaml
kafka-ui:
  image: kafbat/kafka-ui:latest
  container_name: kafka-ui
  ports:
    - "8080:8080"
  environment:
    KAFKA_CLUSTERS_0_NAME: local
    KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:19092
    DYNAMIC_CONFIG_ENABLED: "true"
  depends_on:
    kafka:
      condition: service_healthy
  networks:
    - kafka-net
```

Kafka UI adresi:

```text
http://localhost:8080
```

---

### 5.6 PgAdmin

```yaml
pgadmin:
  image: dpage/pgadmin4:latest
  container_name: pgadmin-gygy5
  environment:
    PGADMIN_DEFAULT_EMAIL: admin@admin.com
    PGADMIN_DEFAULT_PASSWORD: admin
  ports:
    - "5050:80"
```

PgAdmin adresi:

```text
http://localhost:5050
```

---

## 6. Debezium Connector JSON Dosyası

Docker klasörü altında şu dosya oluşturuldu:

```text
docker/debezium/product-outbox-connector.json
```

Bu dosya Debezium'a şunu söyler:

```text
product-db içindeki products database'ine bağlan.
public.outbox tablosunu dinle.
Bu tabloya INSERT gelirse Kafka'ya event gönder.
Eventleri test-topic topic'ine gönder.
```

Dosya içeriği:

```json
{
  "name": "product-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "plugin.name": "pgoutput",

    "database.hostname": "product-db",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "test12345",
    "database.dbname": "products",

    "topic.prefix": "product-service",
    "table.include.list": "public.outbox",

    "publication.autocreate.mode": "filtered",
    "slot.name": "product_outbox_slot",

    "snapshot.mode": "initial",
    "tombstones.on.delete": "false",

    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",

    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",

    "transforms.outbox.route.by.field": "event_type",
    "transforms.outbox.route.topic.replacement": "test-topic",

    "transforms.outbox.table.expand.json.payload": "true",

    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false"
  }
}
```

Önemli ayarlar:

```json
"database.hostname": "product-db"
```

Debezium container içinden PostgreSQL container'a bu isimle ulaşır.

```json
"database.port": "5432"
```

Container içinden product-db'nin kendi portu kullanılır.

```json
"database.dbname": "products"
```

Dinlenecek database.

```json
"table.include.list": "public.outbox"
```

Sadece outbox tablosu dinlenir.

```json
"transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter"
```

Outbox tablosundaki satırı Kafka eventine çevirir.

```json
"transforms.outbox.route.topic.replacement": "test-topic"
```

Eventlerin gönderileceği Kafka topic'i.

---

## 7. Connector Register Etme

Connector dosyası `docker/debezium` klasörü altındaysa, docker klasöründen şu komut çalıştırılır:

```bash
cd /Users/aymina/turkcell-microservices-spring-gygy-5/docker

curl -i -X POST   -H "Accept:application/json"   -H "Content-Type:application/json"   http://localhost:8083/connectors   --data @debezium/product-outbox-connector.json
```

Connector listesi:

```bash
curl http://localhost:8083/connectors
```

Beklenen çıktı:

```json
["product-outbox-connector"]
```

Status kontrolü:

```bash
curl http://localhost:8083/connectors/product-outbox-connector/status
```

Beklenen çıktı:

```json
{
  "name": "product-outbox-connector",
  "connector": {
    "state": "RUNNING"
  },
  "tasks": [
    {
      "id": 0,
      "state": "RUNNING"
    }
  ],
  "type": "source"
}
```

---

## 8. Çalıştırma Komutları

Docker servislerini ayağa kaldırma:

```bash
cd /Users/aymina/turkcell-microservices-spring-gygy-5/docker
docker compose up -d
```

Temiz yeniden başlatma:

```bash
docker compose down
docker compose up -d
```

Container kontrolü:

```bash
docker ps
```

Beklenen containerlar:

```text
kafka
kafka-ui
debezium-connect
product-db
user-db
pgadmin-gygy5
```

Product Service:

```bash
cd /Users/aymina/turkcell-microservices-spring-gygy-5/microservices
mvn spring-boot:run -pl product-service
```

User Service:

```bash
cd /Users/aymina/turkcell-microservices-spring-gygy-5/microservices
mvn spring-boot:run -pl user-service
```

---

## 9. Test Adımları

### 9.1 Connector çalışıyor mu?

```bash
curl http://localhost:8083/connectors/product-outbox-connector/status
```

Beklenen:

```text
connector RUNNING
task RUNNING
```

---

### 9.2 Product Service endpoint çağırma

```http
GET http://localhost:8082/api/products?message=DebeziumTest
```

Beklenen response:

```text
Başarılı
```

Bu işlem Kafka'ya direkt mesaj göndermez, sadece outbox tablosuna kayıt atar.

---

### 9.3 Outbox tablosunu kontrol etme

```bash
docker exec -it product-db psql -U postgres -d products
```

```sql
SELECT id, event_type, payload, status
FROM outbox
ORDER BY created_at DESC
LIMIT 5;
```

Beklenen örnek:

```text
id                                   | event_type | payload                                                       | status
-------------------------------------+------------+---------------------------------------------------------------+---------
d4db2a0c-0296-4289-97c1-f668c2f5c9e3 | testEvent  | {"eventId":"...","message":"DebeziumTest","productId":"..."} | PENDING
```

`PENDING` kalması Debezium yapısında normaldir.

---

### 9.4 Kafka topic kontrolü

Kafka UI:

```text
http://localhost:8080
```

Şuraya bakılır:

```text
Topics -> test-topic -> Messages
```

Terminalden kontrol:

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh   --bootstrap-server kafka:19092   --topic test-topic   --from-beginning   --timeout-ms 10000
```

Burada `DebeziumTest` mesajı görülmelidir.

---

### 9.5 User Service processed_events kontrolü

```bash
docker exec -it user-db psql -U postgres -d users
```

```sql
SELECT * FROM processed_events;
```

Beklenen:

```text
event_id                              | processed_at
--------------------------------------+-------------------------------
d4db2a0c-0296-4289-97c1-f668c2f5c9e3 | 2026-...
```

Eğer tablo boşsa akış şu noktalardan birinde kopmuş olabilir:

```text
1. Debezium Kafka'ya mesaj göndermemiş olabilir.
2. Mesaj yanlış topic'e gitmiş olabilir.
3. User Service çalışmıyor olabilir.
4. User Service farklı topic dinliyor olabilir.
5. Kafka mesaj formatı User Service TestEvent record'u ile uyuşmuyor olabilir.
```


## 11. Ödevin Ana Mantığı

Bu ödevde yapılan ana değişiklik:

```text
OutboxPoller kaldırıldı.
Debezium CDC eklendi.
```

Eski yapı:

```text
Product Service
  ↓
outbox tablosuna kayıt
  ↓
OutboxPoller DB'yi sürekli sorgular
  ↓
Kafka'ya gönderir
```

Yeni yapı:

```text
Product Service
  ↓
outbox tablosuna kayıt
  ↓
Debezium PostgreSQL WAL üzerinden değişikliği yakalar
  ↓
Outbox Event Router ile Kafka mesajına çevirir
  ↓
test-topic'e gönderir
  ↓
User Service eventi işler
```

Bu yaklaşım daha profesyoneldir çünkü:

- Product Service Kafka gönderim detaylarıyla ilgilenmez.
- Sürekli DB sorgulama yapılmaz.
- Event aktarımı veritabanı değişikliklerinden otomatik yakalanır.
- Outbox pattern ile event kaybı riski azalır.
- User Service tarafında idempotency ile aynı eventin tekrar işlenmesi engellenir.

---
