# Mikroservis Ders Notu — Kısa ve Önemli Özet

> Ana konu: **Spring Cloud ile Eureka, Gateway ve Load Balancing mantığı**

---

## 1. Bu derste ne anlatıldı?

Bu derste mikroservislerde servislerin birbirini nasıl bulduğu ve isteklerin doğru servise nasıl yönlendirildiği anlatıldı.

Temel akış:

```text
Kullanıcı / Frontend
        |
        v
Gateway Server
        |
        v
Eureka Server'dan aktif servisleri öğrenir
        |
        v
user-service / product-service / order-service
```

Yani dış dünya artık her servisin portunu bilmez. Tek giriş noktası **Gateway** olur.

Örnek:

```text
http://localhost:8888/api/users
http://localhost:8888/api/products
```

---

## 2. Service ve Server farkı

Mikroservislerde isimlendirme kültürü önemlidir.

### Service

İş yapan uygulamalardır.

```text
user-service
product-service
order-service
```

Örneğin `user-service` kullanıcı işlemlerini yapar.

### Server

Altyapı görevi yapan uygulamalardır.

```text
eureka-server
gateway-server
config-server
```

Örneğin `eureka-server` servislerin kaydını tutar.

---

## 3. Spring Cloud nedir?

**Spring Cloud**, mikroservislerde kullanılan bir paket topluluğudur.

İçinde şunlar olabilir:

```text
Eureka Server  -> Servis keşfi
Eureka Client  -> Servisin kendini Eureka'ya kaydetmesi
Gateway        -> Tek giriş noktası
Load Balancer  -> İstekleri instance'lara dağıtma
Config Server  -> Merkezi ayar yönetimi
```

Spring Cloud kullanırken genelde parent `pom.xml` içine `dependencyManagement` eklenir.

---

## 4. Parent POM mantığı

Multi-module Maven projelerinde en üstte bir `pom.xml` olur.

Örnek yapı:

```text
microservices/
├── pom.xml
├── eureka-server/
├── gateway-server/
├── user-service/
└── product-service/
```

Ana `pom.xml` içindeki modüller:

```xml
<modules>
    <module>eureka-server</module>
    <module>gateway-server</module>
    <module>user-service</module>
    <module>product-service</module>
</modules>
```

Tüm projeyi build etmek için:

```bash
mvn clean install
```

Sadece bir servisi çalıştırmak için:

```bash
mvn spring-boot:run -pl user-service
```

Buradaki `-pl`:

```text
project list
```

Yani Maven’a “bu modülü çalıştır” demektir.

---

## 5. Parent POM’a ne eklenir?

Kural:

```text
Bütün modüller kullanıyorsa parent pom'a eklenir.
Sadece bir servis kullanıyorsa kendi pom.xml'ine eklenir.
```

Örnek:

```text
Spring Cloud dependency management -> parent pom
Eureka Server dependency           -> sadece eureka-server
Gateway dependency                 -> sadece gateway-server
Database dependency                -> DB kullanan servis
```

Önemli not:

```text
Tüm servisler Eureka Client olabilir.
Ama Eureka Server kendini kendine kaydetmek zorunda değildir.
```

---

## 6. Monorepo ve Polyrepo

### Monorepo

Tüm servisler tek Git reposunda tutulur.

```text
Tek repo:
customer-service
product-service
order-service
gateway-server
eureka-server
```

Local development için daha kolaydır.

### Polyrepo

Her servis ayrı Git reposunda tutulur.

```text
customer-service repo
product-service repo
order-service repo
```

Daha bağımsızdır ama ortak parent pom yönetimi daha zahmetlidir.

Polyrepo’da ortak parent pom yapılabilir ama genelde ayrı bir artifact olarak publish etmek gerekir.

---

## 7. Eureka Server nedir?

Eureka Server, çalışan servislerin kaydını tutar.

```text
Eureka Server = Sistemde hangi servis çalışıyor, hangi portta çalışıyor bilen kayıt merkezi
```

Örnek:

```text
user-service     -> localhost:8081
product-service  -> localhost:8082
gateway-server   -> localhost:8888
```

---

## 8. Eureka Server kodu

Ana class içine `@EnableEurekaServer` eklenir.

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

En önemli annotation:

```java
@EnableEurekaServer
```

Bu annotation uygulamayı Eureka Server yapar.

---

## 9. Eureka Server application.yml

Eureka genelde `8761` portunda çalışır.

```yaml
server:
  port: 8761

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

### `register-with-eureka: false`

```text
Eureka Server kendini kendine kaydetmesin.
```

### `fetch-registry: false`

```text
Eureka Server başka servis listesini client gibi çekmesin.
```

---

## 10. Eureka Client nedir?

Eureka Client, kendini Eureka Server’a kaydeden uygulamadır.

Örneğin `user-service` ayağa kalkınca Eureka’ya şunu söyler:

```text
Ben user-service'im.
Şu portta çalışıyorum.
Bana istek gelebilir.
```

---

## 11. Service application.yml örneği

```yaml
server:
  port: 8081

spring:
  application:
    name: user-service

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

Buradaki en önemli kısım:

```yaml
spring.application.name: user-service
```

Çünkü Gateway bu isimle yönlendirme yapar.

---

## 12. Çalıştırma sırası

Çalıştırma sırası önemlidir.

```text
1. eureka-server
2. user-service
3. product-service
4. gateway-server
```

Çünkü servisler ayağa kalkarken Eureka’ya kayıt olmaya çalışır.

Komut örneği:

```bash
mvn spring-boot:run -pl eureka-server
mvn spring-boot:run -pl user-service
mvn spring-boot:run -pl gateway-server
```

---

## 13. Heartbeat nedir?

Servisler belirli aralıklarla Eureka’ya sinyal gönderir.

```text
Ben hâlâ çalışıyorum.
Beni sistemden silme.
```

Buna **heartbeat** denir.

Servis kapanırsa Eureka bunu bir süre sonra fark eder ve listeden çıkarır.

---

## 14. Gateway Server nedir?

Gateway, sistemin dış dünyaya açılan tek kapısıdır.

```text
Frontend -> Gateway -> Mikroservis
```

Gateway’in görevleri:

```text
İstekleri yönlendirmek
Tek porttan sistemi açmak
Eureka üzerinden aktif servisleri bulmak
Load balancing ile uygun instance'a göndermek
```

Gateway için derste örnek port:

```yaml
server:
  port: 8888
```

---

## 15. Gateway route mantığı

Gateway gelen path’e bakar ve ilgili servise yönlendirir.

```yaml
spring:
  application:
    name: gateway-server

server:
  port: 8888

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

cloud:
  gateway:
    server:
      webmvc:
        routes:
          - id: user-service
            uri: lb://user-service
            predicates:
              - Path=/api/users/**
```

Önemli kısımlar:

```text
Path=/api/users/**   -> Bu path gelirse route çalışır.
uri: lb://user-service -> Eureka'daki user-service'e load balanced git.
```

---

## 16. `lb://user-service` ne demek?

```text
lb = load balanced
```

Yani:

```text
Eureka'da kayıtlı user-service instance'larından birine git.
```

Artık port bilmeye gerek kalmaz.

Kötü kullanım:

```text
http://localhost:8081/api/users
```

Daha doğru kullanım:

```text
lb://user-service
```

---

## 17. Load Balancing nedir?

Load balancing, gelen istekleri aynı servisin birden fazla instance’ı arasında dağıtmaktır.

Örnek:

```text
user-service instance 1 -> 8081
user-service instance 2 -> 8082
user-service instance 3 -> 8083
```

İstek dağılımı:

```text
1. istek -> 8081
2. istek -> 8082
3. istek -> 8083
4. istek -> 8081
```

Amaç:

```text
Tek instance'a aşırı yük binmesin.
Sistem daha ölçeklenebilir olsun.
Bir instance kapanırsa diğerleri çalışmaya devam etsin.
```

---

## 18. Round Robin nedir?

Round Robin, en temel load balancing algoritmalarından biridir.

En önemli not:

```text
Round Robin en müsait instance'ı seçmez.
Sıradaki instance'ı seçer.
```

Örnek:

```text
Instance listesi: A, B, C

1. istek -> A
2. istek -> B
3. istek -> C
4. istek -> A
5. istek -> B
```

Yani response time’a bakmaz. Sadece sıraya bakar.

---

## 19. Round Robin neden kullanılır?

### 1. Stateless sistemlerde uygundur

Stateless demek:

```text
Sunucu önceki isteği hatırlamak zorunda değildir.
Her istek kendi başına işlenebilir.
```

Bu durumda isteğin hangi instance’a gittiği çok önemli değildir.

### 2. O(1) time complexity

Round Robin çok hızlıdır.

```text
index = lastUsedIndex % instanceCount
```

Bu işlem sabit zamanlıdır:

```text
O(1)
```

### 3. Homojen ortamlarda risksizdir

Homojen ortam demek:

```text
Tüm instance'ların kapasitesi benzerdir.
CPU/RAM benzerdir.
Response time benzerdir.
```

Bu durumda sırayla dağıtım mantıklı çalışır.

---

## 20. Round Robin basit kod örneği

```java
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinExample {

    private final List<String> instances = List.of(
            "user-service-8081",
            "user-service-8082",
            "user-service-8083"
    );

    private final AtomicInteger lastUsedIndex = new AtomicInteger(0);

    public String getNextInstance() {
        int index = lastUsedIndex.getAndIncrement();
        int selectedIndex = index % instances.size();

        return instances.get(selectedIndex);
    }

    public static void main(String[] args) {
        RoundRobinExample loadBalancer = new RoundRobinExample();

        for (int i = 1; i <= 6; i++) {
            System.out.println(i + ". istek -> " + loadBalancer.getNextInstance());
        }
    }
}
```

Çıktı mantığı:

```text
1. istek -> user-service-8081
2. istek -> user-service-8082
3. istek -> user-service-8083
4. istek -> user-service-8081
```

---

## 21. Black Friday örneği

Notlarda önemli bir soru vardı:

```text
Bir üründen 1 adet kaldı.
Aynı anda birkaç kişi satın almaya çalıştı.
Her istek farklı instance'a giderse problem olur mu?
```

Cevap:

```text
Evet, yanlış tasarlanırsa problem olabilir.
Ama bu load balancing problemi değil, concurrency problemidir.
```

Load balancing sadece isteği dağıtır. Stok tutarlılığını garanti etmez.

---

## 22. Concurrency nedir?

Concurrency, aynı anda birden fazla işlemin aynı veri üzerinde çalışmasıdır.

Örnek:

```text
Aynı ürünü aynı anda 5 kişinin satın almaya çalışması
Aynı banka hesabından aynı anda para çekilmesi
Aynı koltuğu aynı anda iki kişinin almaya çalışması
```

Burada sistemin **thread-safe** olması gerekir.

```text
Thread-safe = Aynı anda çok istek gelse bile veri bozulmamalı.
```

---

## 23. Yanlış stok azaltma örneği

```java
public void buyProduct(UUID productId) {
    Product product = productRepository.findById(productId)
            .orElseThrow();

    if (product.getStock() <= 0) {
        throw new RuntimeException("Stok yok");
    }

    product.setStock(product.getStock() - 1);

    productRepository.save(product);
}
```

Bu kod tek kullanıcıda doğru gibi görünür.

Ama aynı anda iki kullanıcı gelirse ikisi de stok `1` okuyabilir ve stok hatalı düşebilir.

---

## 24. Bu problem nasıl çözülür?

Bunun için load balancing yeterli değildir.

Kullanılabilecek çözümler:

```text
Transaction
Optimistic locking
Pessimistic locking
Idempotency key
Message queue ile sıraya alma
Stok işlemini tek servis üzerinden yönetme
```

Önemli cümle:

```text
Load balancing isteği dağıtır.
Concurrency kontrolü veri tutarlılığını sağlar.
```

---

## 25. Derste akılda kalması gerekenler

```text
Eureka Server servis kayıt merkezidir.

Eureka Client servislerin kendini Eureka'ya kaydetmesini sağlar.

Gateway dış dünyaya açılan tek kapıdır.

Gateway route kurallarına göre istekleri servislere yönlendirir.

lb://user-service, Eureka'daki user-service instance'larından birine git demektir.

Load balancing istekleri instance'lar arasında dağıtır.

Round Robin en müsait instance'ı değil, sıradaki instance'ı seçer.

Round Robin stateless ve homojen sistemlerde kullanışlıdır.

Round Robin O(1) çalıştığı için hızlıdır.

Black Friday stok problemi load balancing değil concurrency problemidir.

Concurrency problemleri transaction ve locking gibi yöntemlerle çözülür.
```

---

## 26. Tek cümlelik özet

```text
Bu derste mikroservislerde Eureka ile servislerin nasıl keşfedildiğini, Gateway ile isteklerin tek kapıdan nasıl yönlendirildiğini ve Load Balancing ile aynı servisin birden fazla instance'ı arasında isteklerin nasıl dağıtıldığını öğrendik.
```
