# Mikroservis Ders Notu — Async İletişim, Kafka, Producer/Consumer

> Ana konu: **Mikroservislerde senkron iletişim yerine Kafka ile asenkron event iletişimi kurmak**  
> Ders amacı: Bir servisin başka servisi direkt beklemeden Kafka üzerinden haberleşmesini, producer/consumer mantığını, idempotency ve transactional outbox problemlerini anlamak.

---

## 1. Bu derste ne anlatıldı?

Bu derste mikroservislerde servislerin birbirleriyle **senkron** ve **asenkron** nasıl haberleştiği anlatıldı.

Önceki mantıkta servisler birbirine direkt istek atıyordu:

```text
order-service ---> product-service
order-service ---> payment-service
```

Bu yapı **senkron iletişimdir**. Yani `order-service`, `product-service` cevap verene kadar bekler.

Bu derste ise şu yapı işlendi:

```text
order-service ---> Kafka ---> product-service
                       ---> payment-service
                       ---> notification-service
```

Yani `order-service` diğer servisleri tek tek çağırmak yerine Kafka'ya bir **event/message** bırakır. Diğer servisler ilgilendikleri eventi Kafka üzerinden dinler.

---

## 2. Senkron iletişim nedir?

Senkron iletişimde bir servis başka bir servise gider ve cevap bekler.

Dersteki örnek:

```text
Client sipariş oluşturmak ister.
Sipariş isteğinde ürün id = 10, quantity = 20 olsun.
order-service product-service'e sorar:
"10 id'li üründen 20 adet stokta var mı?"
```

Basit kod mantığı:

```java
// order-service içinde düşün
boolean stockAvailable = productClient.checkStock(10, 20);

if (!stockAvailable) {
    throw new RuntimeException("Stok yok");
}

order.setStatus(OrderStatus.CONFIRMED);
orderRepository.save(order);
```

Burada `order-service`, `product-service` cevap dönene kadar bekler.

### Senkron iletişimin problemi

```text
product-service yavaşsa order-service de yavaşlar.
product-service kapalıysa order-service işlem yapamayabilir.
Bir servis patlarsa diğer servislerin akışı da etkilenebilir.
Çok yoğun sistemlerde bütün sipariş süreci yavaşlayabilir.
```

Özellikle Amazon gibi çok yoğun sistemlerde her işlemi senkron yapmak sistemi yavaşlatabilir.

---

## 3. Asenkron iletişim nedir?

Asenkron iletişimde servis mesajı bırakır ve yoluna devam eder.

```text
order-service Kafka'ya event gönderir.
product-service bu eventi sonra işler.
payment-service bu eventi sonra işler.
notification-service bu eventi sonra işler.
```

Ana fikir:

```text
Servisler birbirini direkt beklemez.
Servisler event/message üzerinden haberleşir.
```

Örnek sipariş akışı:

```text
1. Client sipariş oluşturur.
2. order-service siparişi PENDING olarak kaydeder.
3. order-service Kafka'ya OrderCreatedEvent gönderir.
4. product-service stok kontrolü yapar.
5. payment-service ödeme kontrolü yapar.
6. notification-service kullanıcıya bilgi gönderebilir.
```

---

## 4. Ders şeması: Async iletişim genel akışı

```text
                         ASYNC İLETİŞİM

Client
  |
  | POST /orders
  v
+----------------+
| order-service  |
| status=PENDING |
+----------------+
  |
  | OrderCreatedEvent
  v
+----------------+
|     KAFKA      |
|  order-topic   |
+----------------+
  |          |           |
  |          |           |
  v          v           v
product   payment   notification
service   service   service
  |          |           |
stok      ödeme      bildirim
kontrolü  kontrolü   gönderimi
```

Bu yapıda `order-service`, `product-service` veya `payment-service` cevap versin diye beklemez. Eventi Kafka'ya bırakır.

---

## 5. Amazon sipariş örneği

Derste şu örnek üzerinden anlatıldı:

```text
Amazon'da sipariş verirsin.
Sistem bazen ödemeyi/stoku anlık tamamen doğrulamadan siparişi almış gibi gösterebilir.
Kart bakiyesi yoksa veya stokta problem varsa birkaç dakika sonra sipariş iptal olabilir.
```

Bu ilk bakışta hata gibi gelebilir ama büyük sistemlerde bilinçli bir tercih olabilir.

Çünkü senaryoların çoğu başarılıdır:

```text
%90 olumlu senaryo  -> ödeme başarılı, stok var
%10 olumsuz senaryo -> stok bitmiş, ödeme başarısız
```

Eğer %10 olumsuz senaryo yüzünden herkesin siparişini yavaşlatırsak sistem kötü çalışır.

Akılda kalacak cümle:

```text
Olumlu senaryolar çoğunluktaysa, sistemi herkes için yavaşlatmak yerine ana akışı hızlı tutup olumsuz durumları sonradan eventlerle yönetebiliriz.
```

---

## 6. Sipariş status mantığı

Asenkron sistemlerde sipariş genelde hemen `CONFIRMED` yapılmaz.

Önce bekleme durumuna alınır:

```java
Order order = new Order();
order.setStatus(OrderStatus.PENDING);
orderRepository.save(order);
```

Status mantığı:

```text
PENDING   -> Sipariş alındı ama süreç tamamlanmadı.
CONFIRMED -> Stok ve ödeme başarılı.
CANCELED  -> Stok veya ödeme başarısız.
```

Akış:

```text
Client sipariş oluşturur.
order-service siparişi PENDING kaydeder.
Kafka'ya OrderCreatedEvent gönderilir.
product-service stok kontrolü yapar.
payment-service ödeme kontrolü yapar.
Sorun yoksa sipariş CONFIRMED olur.
Sorun varsa sipariş CANCELED olur.
```

---

## 7. Bu Saga Pattern midir?

Derste önemli bir soru vardı:

```text
Hocam bu örnek bizim için Saga Pattern örneği midir?
Yoksa Saga'nın ayrıştığı noktalar var mı?
```

Cevap:

```text
Sadece order-service'in event göndermesi tek başına Saga değildir.
```

Saga diyebilmek için diğer servislerin sonucu tekrar `order-service` tarafına event olarak bildirmesi gerekir.

Örnek Saga akışı:

```text
order-service -> OrderCreatedEvent gönderir.
product-service -> StockReservedEvent veya StockFailedEvent döner.
payment-service -> PaymentCompletedEvent veya PaymentFailedEvent döner.
order-service bu cevaplara göre order status değiştirir.
```

Şema:

```text
+---------------+       OrderCreatedEvent       +-------+
| order-service | ----------------------------> | Kafka |
+---------------+                               +-------+
      ^                                             |
      |                                             |
      | StockFailedEvent / PaymentFailedEvent       v
      |                                      +----------------+
      +--------------------------------------| product-service|
      |                                      +----------------+
      |
      | PaymentCompletedEvent / PaymentFailedEvent
      |
      v
+----------------+
| payment-service|
+----------------+
```

Akılda kalacak cümle:

```text
Saga, servislerin birbirine ters eventlerle sonucu bildirmesi ve ana sürecin bu eventlere göre status yönetmesidir.
```

Bu dersteki çizimde tam bir Saga yoktu. Çünkü diğer servislerin sonucu order-service'e geri event olarak dönmesi henüz kurulmamıştı.

---

## 8. Async iletişim aslında Message Queue mantığıdır

Derste geçen ifade:

```text
Async iletişim aslında message queue dediğimiz sistemlerdir.
```

Basit düşün:

```text
Servis mesajı kuyruğa bırakır.
Diğer servis kuyruğu dinler.
Mesaj geldiğinde işler.
```

Gerçek hayat benzetmesi:

```text
Restorana sipariş verirsin.
Garson siparişi mutfağa iletir.
Sen mutfağın başında beklemezsin.
Mutfak siparişi sıraya alır ve hazırlar.
```

Kafka da mikroservisler arasında bu sipariş fişi gibi davranır.

---

## 9. Kafka nedir?

Kafka sadece klasik anlamda bir message queue değildir.

Derste şöyle anlatıldı:

```text
Kafka, message queue sistemidir ama daha doğru düşünceyle dağıtık bir commit log gibi düşünülebilir.
```

Kafka'nın temel kavramları:

```text
Topic      -> Mesajların başlığı/kategorisi
Partition  -> Topic'in parçalara ayrılmış hali
Offset     -> Mesajın partition içindeki sırası
Producer   -> Mesajı gönderen servis
Consumer   -> Mesajı dinleyen servis
Group      -> Consumer grubunun adı
```

Bu derste topic, producer, consumer ve group mantığına giriş yapıldı. Partition ve offset detaylarının sonra işleneceği söylendi.

---

## 10. Topic nedir?

Topic, mesajların gönderildiği başlıktır.

Örnek topic isimleri:

```text
test-topic
order-topic
product-topic
payment-topic
```

Kafka mesajı kime göndereceğini topic üzerinden bilir.

```text
Bir servis topic'e mesaj yazar.
Diğer servis o topic'i dinler.
```

Örnek:

```text
product-service ---> test-topic ---> user-service
```

---

## 11. Producer nedir?

Producer, Kafka'ya mesaj gönderen servistir.

Bu dersteki örnekte:

```text
product-service = producer
```

Çünkü `product-service`, Kafka'ya `TestEvent` gönderdi.

Kodda mesaj gönderme:

```java
streamBridge.send("testEvent-out-0", event);
```

Burada:

```text
testEvent-out-0 -> binding name
event           -> Kafka'ya gönderilecek nesne
```

---

## 12. Consumer / Subscriber nedir?

Consumer veya subscriber, Kafka'daki mesajı dinleyen servistir.

Bu dersteki örnekte:

```text
user-service = consumer/subscriber
```

Çünkü `user-service`, `test-topic` üzerinden gelen `TestEvent` mesajını dinledi.

Kod mantığı:

```java
@Bean
public Consumer<TestEvent> consumeTestEvent() {
    return event -> {
        System.out.println("TestEvent İŞLENDİ: " + event.message());
    };
}
```

Consumer uygulama ayağa kalktığı anda Kafka'ya bağlanır ve mesaj beklemeye başlar.

---

## 13. Consumer Group nedir?

Consumer group, aynı işi yapan consumer'ların grup adıdır.

Örnek:

```yaml
group: user-service-group
```

Bu şu anlama gelir:

```text
user-service bu topic'i user-service-group adıyla dinliyor.
```

Mikroservis yapısı gereği aynı anda birden fazla instance çalışabilir:

```text
payment-service-1
payment-service-2
payment-service-3
payment-service-4
payment-service-5
```

Kafka, aynı grup içindeki consumer'lara mesajları dağıtabilir.

Ama önemli nokta:

```text
Aynı mesajın yanlışlıkla tekrar gelme ihtimali yine de vardır.
Bu yüzden idempotency gerekir.
```

---

## 14. Async iletişimde dikkat edilmesi gereken iki büyük problem

Kafka güçlüdür ama kendi problemleri vardır.

Derste özellikle iki konu işlendi:

```text
1. Idempotency
2. Transactional Outbox Pattern
```

Bunlar şunun için gerekir:

```text
Consumer tarafında aynı event tekrar işlenmesin.
Producer tarafında DB kaydı ile Kafka mesajı arasında tutarsızlık olmasın.
```

---

## 15. Idempotency nedir?

Idempotency, aynı event yanlışlıkla iki kere gelse bile işlemin sadece bir kere yapılmasıdır.

Dersteki problem:

```text
Kafka aynı ödeme eventini payment-service'e yanlışlıkla iki kere gönderebilir.
Ack fail / tanıma hatası gibi durumlar olabilir.
Eğer önlem yoksa kullanıcı kartından 2-3 kere para çekilebilir.
```

Bu çok ciddi bir hatadır.

Bu yüzden consumer tarafında şu kontrol yapılır:

```text
Bu event_id daha önce işlendi mi?
Evet -> tekrar işleme.
Hayır -> işle ve işlendi diye kaydet.
```

---

## 16. Inbox Table nedir?

Inbox table, consumer tarafında işlenen eventleri kaydetmek için kullanılan tablodur.

Örnek tablo:

```text
payment_service_inbox
---------------------
event_id
processed_at
status
```

Akış:

```text
1. Event geldi.
2. payment-service inbox table'a baktı.
3. event_id daha önce işlendiyse işlemi durdurdu.
4. event_id yoksa ödeme işlemini yaptı.
5. event_id'yi inbox table'a kaydetti.
```

Şema:

```text
Kafka
  |
  | PaymentRequestedEvent(event_id = a)
  v
payment-service
  |
  | inbox table kontrolü
  v
+---------------------------+
| event_id | processed_at   |
| a        | 01.06.2026 ... |
+---------------------------+

Eğer event_id = a daha önce varsa:
2. kez işleme alma.
```

Basit kod mantığı:

```java
public void handlePaymentEvent(PaymentRequestedEvent event) {
    if (inboxRepository.existsByEventId(event.eventId())) {
        return; // daha önce işlenmiş, tekrar para çekme
    }

    paymentService.withdraw(event.userId(), event.amount());

    inboxRepository.save(new InboxMessage(
            event.eventId(),
            LocalDateTime.now(),
            "SUCCESS"
    ));
}
```

Akılda kalacak cümle:

```text
Kafka'ya tamamen güvenme. Eventin tekrar gelebileceğini düşün ve kendi idempotency mekanizmanı kur.
```

---

## 17. Producer tarafında ne hata olabilir?

Producer tarafında problem şudur:

```text
Event mesaj göndermek anlık bir olaydır.
Tam mesaj gönderileceği anda Kafka düşerse / ulaşılamazsa ne olur?
```

Örnek kötü senaryo:

```text
1. order-service siparişi database'e kaydetti.
2. Ama Kafka'ya OrderCreatedEvent gönderirken Kafka çöktü.
3. Database'de sipariş var.
4. product-service ve payment-service bu siparişten haberdar değil.
```

Bu durumda sistem tutarsız olur.

Çözüm:

```text
Transactional Outbox Pattern
```

---

## 18. Transactional Outbox Pattern nedir?

Transactional Outbox Pattern, producer tarafında DB işlemi ile event gönderimini güvenli hale getiren yapıdır.

Ana fikir:

```text
Mesajı direkt Kafka'ya göndermek yerine önce kendi database'indeki outbox tablosuna yaz.
Sonra ayrı bir poller/job bu outbox kayıtlarını Kafka'ya göndersin.
```

---

## 19. Ghost Message problemi

Derste geçen kavram:

```text
Ghost message
```

Ghost message, DB işlemi ile Kafka mesajı arasında tutarsızlık oluşmasıdır.

İki kötü senaryo:

```text
1. DB'ye kayıt atıldı ama Kafka'ya mesaj gönderilemedi.
2. Kafka'ya mesaj gitti ama DB işlemi tamamlanamadı.
```

Bu yüzden mesajı direkt Kafka'ya göndermek yerine önce outbox tablosuna yazmak daha güvenlidir.

---

## 20. Outbox Table nedir?

Producer servis kendi database'inde gönderilecek eventleri bir tabloya kaydeder.

Dersteki outbox alanları:

```text
outbox
------
event_name
event_json
created_at
sent_at
retry_count
last_error
status
```

Daha detaylı tablo:

```text
outbox
------
id
event_name
event_json
created_at
sent_at
retry_count
last_error
status
```

Akış:

```text
1. order-service siparişi kaydeder.
2. Aynı transaction içinde outbox tablosuna OrderCreatedEvent yazar.
3. Ayrı bir job/poller outbox tablosunu kontrol eder.
4. Gönderilmemiş eventleri Kafka'ya yollar.
5. Başarılı olursa status SENT yapılır.
6. Başarısız olursa retry_count artırılır ve last_error yazılır.
```

---

## 21. Transactional Outbox şeması

```text
Client
  |
  | POST /orders
  v
+----------------+
| order-service  |
+----------------+
  |
  | Aynı DB transaction içinde:
  | 1. orders tablosuna sipariş yaz
  | 2. outbox tablosuna event yaz
  v
+------------------------+
| order-service database |
|                        |
| orders                 |
| outbox                 |
+------------------------+
  |
  | Poller gönderilmemiş eventleri okur
  v
+----------------+
|     Kafka      |
+----------------+
  |
  v
product-service / payment-service / notification-service
```

---

## 22. Transactional Outbox kod mantığı

Sipariş oluştururken hem order hem outbox aynı transaction içinde kaydedilir.

```java
@Transactional
public void createOrder(CreateOrderCommand command) {
    Order order = new Order();
    order.setStatus(OrderStatus.PENDING);
    orderRepository.save(order);

    OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            command.items(),
            command.userId()
    );

    OutboxMessage outboxMessage = new OutboxMessage(
            UUID.randomUUID(),
            "OrderCreatedEvent",
            objectMapper.writeValueAsString(event),
            "PENDING"
    );

    outboxRepository.save(outboxMessage);
}
```

Sonra ayrı bir poller çalışır:

```java
@Scheduled(fixedDelay = 5000)
public void publishPendingEvents() {
    List<OutboxMessage> messages = outboxRepository.findByStatus("PENDING");

    for (OutboxMessage message : messages) {
        try {
            streamBridge.send("orderCreatedEvent-out-0", message.getEventJson());
            message.setStatus("SENT");
            message.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            message.increaseRetryCount();
            message.setLastError(e.getMessage());
        }
    }
}
```

Akılda kalacak cümle:

```text
Producer tarafında outbox, consumer tarafında inbox/idempotency kullanılır.
```

---

## 23. Docker Compose nedir?

Docker Compose, birden fazla container'ı tek dosya ile ayağa kaldırmayı sağlar.

Normalde tek tek komut yazmak gerekir:

```bash
docker run kafka
docker run kafka-ui
```

Docker Compose ile bunları tek dosyada toplarız.

```bash
docker compose up -d
```

Bu komut şunu yapar:

```text
Kafka container'ını ayağa kaldırır.
Kafka UI container'ını ayağa kaldırır.
Network ve volume ayarlarını yapar.
```

---

## 24. Docker Compose dosyası nereye konur?

Derste önerilen yapı:

```text
microservices/
├── docker/
│   └── docker-compose.yml
├── product-service/
├── user-service/
└── pom.xml
```

Çalıştırma:

```bash
cd docker
docker compose up -d
```

Kafka UI adresi:

```text
http://localhost:8080
```

---

## 25. Derste kullanılan Kafka Docker Compose dosyası

Aşağıdaki compose dosyası Kafka ve Kafka UI'ı beraber ayağa kaldırır.

```yaml
services:
  kafka:
    image: apache/kafka:4.2.0
    container_name: kafka
    ports:
      - "9092:9092"          # host -> broker (EXTERNAL listener)
    environment:
      # --- KRaft kimlik ve roller ---
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller        # tek node hem broker hem controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093

      # --- Listener tanımları ---
      # Üç ayrı listener: container içi (INTERNAL), host (EXTERNAL), controller
      KAFKA_LISTENERS: INTERNAL://0.0.0.0:19092,EXTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:19092,EXTERNAL://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER

      # --- Tek node olduğu için replikasyon faktörleri 1 ---
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1

      # --- Geliştirme kolaylıkları ---
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0      # consumer'lar hızlı bağlansın
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"        # eğitimde pratik; prod'da false yapın
      KAFKA_NUM_PARTITIONS: 3                         # otomatik topic'ler 3 partition'la doğsun
    volumes:
      - kafka-data:/var/lib/kafka/data
    healthcheck:
      test: ["CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 || exit 1"]
      interval: 10s
      timeout: 10s
      retries: 10
    networks:
      - kafka-net

  kafka-ui:
    image: kafbat/kafka-ui:latest     # provectuslabs/kafka-ui'nin aktif geliştirilen fork'u
    container_name: kafka-ui
    ports:
      - "8080:8080"                   # http://localhost:8080
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:19092
      DYNAMIC_CONFIG_ENABLED: "true"
    depends_on:
      kafka:
        condition: service_healthy
    networks:
      - kafka-net

volumes:
  kafka-data:

networks:
  kafka-net:
    driver: bridge
```

Çalıştırma komutu:

```bash
docker compose up -d
```

Not:

```text
-d = detached mode
Yani terminali kilitlemeden arka planda çalıştırır.
```

---

## 26. Kafka Docker Compose ayarları ne anlama geliyor?

Önemli kısımlar:

```yaml
ports:
  - "9092:9092"
```

Anlamı:

```text
Uygulamalar localhost:9092 üzerinden Kafka'ya bağlanır.
```

```yaml
kafka-ui:
  ports:
    - "8080:8080"
```

Anlamı:

```text
Kafka UI arayüzü http://localhost:8080 adresinden açılır.
```

```yaml
KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
```

Anlamı:

```text
Eğitim ortamında topic yoksa otomatik oluşsun.
```

Production notu:

```text
Gerçek projelerde topic'lerin otomatik oluşması genelde kapatılır.
Topic'ler bilinçli ve kontrollü oluşturulur.
```

---

## 27. Product service ile user service arasında Kafka iletişimi

Bu derste uygulamalı örnek olarak:

```text
product-service -> Kafka -> user-service
```

akışı kuruldu.

Amaç:

```text
product-service bir mesaj yayınlasın.
user-service bu mesajı Kafka üzerinden yakalasın.
```

Şema:

```text
GET /api/products?message=Merhaba
            |
            v
+-----------------+
| product-service |
| Producer        |
+-----------------+
            |
            | TestEvent
            v
+-----------------+
| Kafka           |
| test-topic      |
+-----------------+
            |
            v
+-----------------+
| user-service    |
| Consumer        |
+-----------------+
            |
            v
Console: TestEvent İŞLENDİ: Merhaba
```

---

## 28. Maven bağımlılığı

Kafka ile Spring Cloud Stream üzerinden haberleşmek için ilgili servislere şu dependency eklendi.

`product-service` ve `user-service` pom dosyalarına eklenir:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-stream-kafka</artifactId>
</dependency>
```

Sonra `microservices` klasörü içinde build alınır:

```bash
mvn clean install
```

---

## 29. Event paketi neden oluşturulur?

Eventler genelde proje içinde ayrı bir pakette tutulur.

Örnek yapı:

```text
product-service
└── src/main/java/com/turkcell/product_service
    └── event
        └── TestEvent.java
```

Event, servisler arasında taşınacak mesajın modelidir.

Derste `record` kullanıldı. Çünkü event/DTO tarzı yapılarda kısa ve temizdir.

---

## 30. Product Service event kodu

```java
package com.turkcell.product_service.event;

import java.util.UUID;

public record TestEvent(String message, UUID productId) {
}
```

Burada:

```text
message   -> Göndermek istediğin mesaj
productId -> Örnek olarak üretilen ürün id'si
```

---

## 31. Product Service Producer Controller kodu

`product-service`, Kafka'ya mesaj gönderen taraftır.

```java
package com.turkcell.product_service.controller;

import com.turkcell.product_service.event.TestEvent;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequestMapping("/api/products")
@RestController
public class ProductsController {

    private final StreamBridge streamBridge;

    public ProductsController(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @GetMapping
    public String test(@RequestParam String message) {
        var event = new TestEvent(message, UUID.randomUUID());

        streamBridge.send("testEvent-out-0", event);

        return "Başarılı";
    }
}
```

Bu endpoint çağrılınca:

```text
1. message parametresi alınır.
2. TestEvent nesnesi oluşturulur.
3. streamBridge ile Kafka'ya gönderilir.
4. Cevap olarak "Başarılı" döner.
```

İstek örneği:

```text
GET http://localhost:8082/api/products?message=Merhaba
```

---

## 32. StreamBridge nedir?

`StreamBridge`, Spring Cloud Stream içinde mesaj göndermek için kullanılan köprü yapıdır.

Kod:

```java
streamBridge.send("testEvent-out-0", event);
```

Anlamı:

```text
Ben testEvent-out-0 isimli binding üzerinden event göndermek istiyorum.
```

Spring Cloud Stream bu nesneyi JSON'a çevirir ve konfigürasyonda hangi topic'e bağlıysa oraya gönderir.

Güzel tarafı:

```text
Kodun içinde Kafka topic adı yok.
Kafka detayı application.yml içinde duruyor.
```

---

## 33. Product Service application.yml

Producer tarafında binding tanımı yapılır.

```yaml
spring:
  application:
    name: product-service
  cloud:
    stream:
      bindings:
        testEvent-out-0:
          destination: test-topic
          content-type: application/json
      kafka:
        binder:
          brokers: localhost:9092
```

Önemli kısım:

```yaml
testEvent-out-0:
  destination: test-topic
```

Anlamı:

```text
testEvent-out-0 binding'i ile gönderilen mesajlar Kafka'daki test-topic'e gitsin.
```

---

## 34. Binding name mantığı

Binding name kod ile config arasında köprü kurar.

Kodda:

```java
streamBridge.send("testEvent-out-0", event);
```

Configte:

```yaml
testEvent-out-0:
  destination: test-topic
```

Bu iki isim aynı olmak zorunda.

Genel kültür:

```text
eventName-out-0
```

Anlamı:

```text
testEvent -> eventin adı
out       -> dışarı mesaj gönderiyor
0         -> index/sıra numarası
```

Eğer mesaj dinliyor olsaydı:

```text
eventName-in-0
```

---

## 35. Destination nedir?

`destination`, Kafka tarafındaki topic adıdır.

```yaml
destination: test-topic
```

Yani:

```text
testEvent-out-0 binding'i ile gönderilen mesajlar test-topic'e gider.
```

Kod topic'i direkt bilmez. Kod binding'i bilir, config binding'in hangi topic'e gideceğini söyler.

---

## 36. User Service tarafında aynı event neden tekrar yazıldı?

`user-service` de gelen mesajı okuyabilmek için aynı event modelini tanımlar.

```java
package com.turkcell.user_service.event;

import java.util.UUID;

public record TestEvent(String message, UUID productId) {
}
```

Bu kendini tekrar etmek gibi görünebilir ama mikroservislerde normaldir.

Çünkü:

```text
product-service ayrı projedir.
user-service ayrı projedir.
Her servis kendi modelini yönetir.
```

Daha büyük projelerde ortak bir `event-contract` paketi yapılabilir. Ama derste basit olması için iki serviste de ayrı tanımlandı.

---

## 37. User Service Consumer kodu

Consumer sınıfı `consumer` paketi içinde oluşturulur.

```java
package com.turkcell.user_service.consumer;

import com.turkcell.user_service.event.TestEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class TestEventConsumer {

    @Bean
    public Consumer<TestEvent> consumeTestEvent() {
        return event -> {
            System.out.println(
                    "TestEvent İŞLENDİ: "
                    + event.message()
                    + ", Product ID: "
                    + event.productId()
            );
        };
    }
}
```

Burada:

```text
@Configuration -> Bu sınıf içinde bean tanımları var.
@Bean          -> Spring bu metodu bean olarak yönetecek.
Consumer<TestEvent> -> TestEvent tipinde mesaj dinleyen fonksiyon.
```

Metot mesaj geldiğinde çalışır.

---

## 38. User Service application.yml

Consumer tarafı:

```yaml
spring:
  application:
    name: user-service
  cloud:
    function:
      definition: consumeTestEvent
    stream:
      bindings:
        consumeTestEvent-in-0:
          destination: test-topic # hangi başlıktan bu eventi dinleyeyim?
          group: user-service-group # hangi grup olarak dinliyorum?
      kafka:
        binder:
          brokers: localhost:9092

server:
  port: 8081

management:
  endpoints:
    web:
      exposure:
        include: "*"
```

Önemli kısımlar:

```yaml
spring.cloud.function.definition: consumeTestEvent
```

Anlamı:

```text
Spring'e bu consumer fonksiyonunu kullan diyoruz.
```

```yaml
consumeTestEvent-in-0:
  destination: test-topic
```

Anlamı:

```text
consumeTestEvent fonksiyonu test-topic'i dinlesin.
```

```yaml
group: user-service-group
```

Anlamı:

```text
Bu consumer user-service-group grubuna ait olarak dinlesin.
```

---

## 39. Çalıştırınca ne oldu?

Derste gözlenen akış:

```text
1. Kafka Docker ile ayağa kaldırıldı.
2. user-service çalıştırıldı.
3. user-service consumer olduğu için direkt Kafka'ya bağlandı ve dinlemeye başladı.
4. product-service çalıştırıldı.
5. product-service endpointine istek atıldı.
6. product-service Kafka'ya TestEvent gönderdi.
7. user-service eventi yakaladı.
8. Konsola TestEvent İŞLENDİ yazdı.
```

İstek:

```text
GET http://localhost:8082/api/products?message=Merhaba
```

User service konsol çıktısı:

```text
TestEvent İŞLENDİ: Merhaba, Product ID: 9d8f...
```

En önemli nokta:

```text
User-service'in controller'ı çağrılmadı.
Product-service, user-service'i Kafka üzerinden haberdar etti.
```

---

## 40. Producer ve Consumer çalışma farkı

Derste şu fark görüldü:

```text
Consumer olan user-service ayağa kalkınca direkt Kafka'ya bağlanır.
Çünkü dinleyicidir, mesaj bekler.
```

```text
Producer olan product-service ise controller'a istek geldiğinde Kafka'ya mesaj gönderir.
Yani olay tetiklenince producer gibi davranır.
```

---

## 41. Kafka UI'da ne görülür?

Kafka UI ile şu bilgiler gözlemlenebilir:

```text
Topic'ler
Mesajlar
Consumer group'lar
Partition bilgileri
Offset bilgileri
```

Derste `test-topic` içine mesaj gittiği Kafka UI üzerinden görüldü.

Ama asıl amaç sadece Kafka UI'da mesaj görmek değildir.

Asıl amaç:

```text
Gönderilen mesajı başka bir servisin okuyabilmesidir.
```

---

## 42. Kodda Kafka isminin geçmemesi neden güzel?

Producer kodu:

```java
streamBridge.send("testEvent-out-0", event);
```

Consumer kodu:

```java
@Bean
public Consumer<TestEvent> consumeTestEvent() {
    return event -> { ... };
}
```

Dikkat:

```text
Kodun içinde Kafka topic adı veya Kafka client kodu yok.
```

Kafka bağlantısı `application.yml` içinde yönetiliyor.

Bu yüzden yarın Kafka yerine RabbitMQ kullanılmak istenirse business kodu daha az etkilenir.

Akılda kalacak cümle:

```text
Kod event gönderdiğini bilir. Hangi message broker'a gittiğini config belirler.
```

---

## 43. Gerçek sipariş akışı nasıl olur?

Ödevde istenen daha gerçekçi akış:

```text
Order-Service -> POST sipariş oluşturur.
Order-Service -> OrderCreatedEvent fırlatır.
Product-Service -> Bu eventteki her ürün için stok işlemi yapar.
```

Şema:

```text
POST /orders
    |
    v
+----------------+
| order-service  |
| Order PENDING  |
+----------------+
    |
    | OrderCreatedEvent
    v
+----------------+
| Kafka          |
| order-topic    |
+----------------+
    |
    v
+----------------+
| product-service|
| stok kontrolü  |
+----------------+
```

---

## 44. OrderCreatedEvent örneği

```java
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID orderId,
        UUID userId,
        List<OrderItemMessage> items
) {
}
```

Item modeli:

```java
import java.util.UUID;

public record OrderItemMessage(
        UUID productId,
        int quantity
) {
}
```

---

## 45. Order Service Producer örneği

Basit versiyon:

```java
@PostMapping
public ResponseEntity<String> createOrder(@RequestBody CreateOrderRequest request) {
    Order order = new Order();
    order.setUserId(request.userId());
    order.setStatus(OrderStatus.PENDING);
    orderRepository.save(order);

    OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            request.userId(),
            request.items()
    );

    streamBridge.send("orderCreatedEvent-out-0", event);

    return ResponseEntity
            .status(HttpStatus.CREATED)
            .body("Sipariş oluşturuldu");
}
```

Bu örnekte direkt Kafka'ya gönderim var.

Daha güvenli versiyonda:

```text
Direkt Kafka'ya göndermek yerine outbox tablosuna yazmak gerekir.
```

---

## 46. Product Service Consumer örneği

Amaç:

```text
OrderCreatedEvent geldiğinde stok düşmek.
Aynı event tekrar gelirse ikinci kez stok düşmemek.
```

```java
@Configuration
public class OrderCreatedConsumer {

    private final ProductRepository productRepository;
    private final InboxRepository inboxRepository;

    public OrderCreatedConsumer(ProductRepository productRepository,
                                InboxRepository inboxRepository) {
        this.productRepository = productRepository;
        this.inboxRepository = inboxRepository;
    }

    @Bean
    public Consumer<OrderCreatedEvent> consumeOrderCreatedEvent() {
        return event -> {
            if (inboxRepository.existsByEventId(event.orderId())) {
                return;
            }

            for (OrderItemMessage item : event.items()) {
                Product product = productRepository.findById(item.productId())
                        .orElseThrow();

                if (product.getStock() < item.quantity()) {
                    throw new RuntimeException("Stok yetersiz");
                }

                product.setStock(product.getStock() - item.quantity());
                productRepository.save(product);
            }

            inboxRepository.save(
                    new InboxMessage(
                            event.orderId(),
                            LocalDateTime.now(),
                            "SUCCESS"
                    )
            );
        };
    }
}
```

---

## 47. Ödevde yapılacak şey

Derste verilen ödev:

```text
Transactional Outbox Pattern ve Idempotency implemente edilmeye çalışılacak.
order-service ile product-service arasında gerçek bir sipariş akış event iletişimi kurulacak.
```

Minimum akış:

```text
1. Order-service içinde POST /orders endpointi yaz.
2. Siparişi PENDING olarak kaydet.
3. OrderCreatedEvent oluştur.
4. Outbox table'a event bilgisini yaz.
5. Poller ile outbox'taki eventleri Kafka'ya gönder.
6. Product-service OrderCreatedEvent'i dinlesin.
7. Product-service stok kontrolü yapsın.
8. Aynı event tekrar gelirse idempotency ile tekrar işlemesin.
```

---

## 48. Bu derste geçen önemli kavramlar

```text
Async iletişim       -> Servislerin birbirini beklemeden event/message ile haberleşmesi
Sync iletişim        -> Servisin başka servise gidip cevap beklemesi
Message Queue        -> Mesajların kuyruğa/topic'e bırakılıp sonra işlenmesi
Kafka                -> Dağıtık commit log mantığında çalışan message broker
Topic                -> Mesaj başlığı/kategorisi
Producer             -> Mesaj gönderen servis
Consumer/Subscriber  -> Mesaj dinleyen servis
Consumer Group       -> Aynı işi yapan consumer grubunun adı
Binding              -> Kod ile broker/topic ayarı arasındaki köprü
Destination          -> Kafka topic adı
StreamBridge         -> Spring Cloud Stream ile mesaj göndermeyi sağlayan köprü
Idempotency          -> Aynı event tekrar gelse bile işlemi bir kez yapmak
Inbox Table          -> Consumer tarafında işlenen event kayıtları
Outbox Table         -> Producer tarafında gönderilecek event kayıtları
Transactional Outbox -> DB kaydı ile event gönderimini güvenli hale getiren pattern
Ghost Message        -> DB ve Kafka arasında tutarsız mesaj durumu
Saga Pattern         -> Servislerin sonuç eventleriyle süreci geri bildirip status yönetmesi
```

---

## 49. Akılda kalması gereken kısa özet

```text
Senkron iletişimde servis cevap bekler.
Asenkron iletişimde servis event gönderir ve yoluna devam eder.
Kafka servisler arasında event/message taşır.
Producer mesajı gönderen servistir.
Consumer mesajı dinleyen servistir.
Topic mesajların başlığıdır.
Binding name kod ile config arasında köprü kurar.
Destination Kafka topic adıdır.
Consumer group aynı işi yapan consumer'ların grup adıdır.
Idempotency aynı event tekrar gelirse işlemi tekrar yapmamaktır.
Inbox table consumer tarafında işlenen eventleri tutar.
Outbox table producer tarafında gönderilecek eventleri tutar.
Transactional Outbox DB kaydı ile event gönderimini güvenli hale getirir.
Saga için servislerin sonucu tekrar event olarak dönmesi gerekir.
Kafka UI'da mesajı görmek yeterli değildir, önemli olan başka servisin o mesajı okuyabilmesidir.
```

---

## 50. Tek cümlelik özet

```text
Bu derste mikroservislerde servislerin birbirini beklemeden Kafka üzerinden event göndererek haberleşmesini, producer-consumer mantığını, topic-binding ayarlarını ve güvenli event iletişimi için idempotency ile transactional outbox pattern kullanımını öğrendik.
```
