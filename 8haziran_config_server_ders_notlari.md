# 8 Haziran Dersi — Spring Cloud Config Server Ders Notları

Bu dosya, derste alınan notların düzenlenmiş ve görsellerin yazıya dökülmüş halidir. Görsel eklenmemiştir; görsellerdeki klasör yapıları, şemalar ve kodlar metin olarak açıklanmıştır.

---

## 1. Config Server Nedir?

**Config Server**, mikroservislerin konfigürasyonlarını merkezi bir yerden okumasını sağlayan yapıdır.

Normalde her mikroservisin kendi içinde bir `application.yaml` dosyası olur. Ancak mikroservis sayısı arttıkça ve her servis için farklı ortamlar oluşunca konfigürasyon yönetimi zorlaşır.

Örneğin bir sistemde:

- `local`
- `test`
- `prod`

olmak üzere 3 farklı ortam olduğunu düşünelim.

Eğer 12 tane servis veya değişken varsa:

```text
12 değişken x 3 ortam = 36 konfigürasyon
```

Bu kadar fazla konfigürasyonu her servisin içinde ayrı ayrı yönetmek zorlaşır.

Bu yüzden konfigürasyonları ortak bir yerden yönetmek daha mantıklıdır.

---

## 2. Neden Config Server Kullanıyoruz?

Config Server kullanmamızın temel sebebi, bütün servislerin config ayarlarını merkezi bir yerden almasını sağlamaktır.

Örneğin her servis içinde ayrı ayrı şu dosyalar olabilir:

```text
application.yaml
application-test.yaml
application-prod.yaml
```

Bir projede çok sayıda mikroservis varsa bu dosyaların sayısı hızla artar.

Örneğin:

```text
15 servis x 4 config dosyası = 60 config dosyası
```

Bu dosyaları her servisin içinde ayrı ayrı yönetmek yerine, tek bir merkezi config repository üzerinden yönetmek daha kolaydır.

Config Server sayesinde:

1. Ortak configler tek yerden yönetilir.
2. Servislerin içinde gereksiz config tekrarı azalır.
3. Ortam bazlı değişiklikler daha kolay yapılır.
4. Config değiştiğinde her servisin kodunu değiştirmeye gerek kalmaz.
5. Configler API gibi dışarıya sunulur.

---

## 3. Docker Compose ile Config Server Aynı Şey mi?

Derste bu soru soruldu:

> Docker Compose bu işe yaramıyor muydu?

Cevap:

Docker Compose, servisleri Docker ortamında ayağa kaldırırken kullanılan bir yapı sağlar. Örneğin servislerin container olarak çalışması, portlarının bağlanması, environment variable verilmesi gibi işleri kolaylaştırır.

Ama burada konuşulan konu Docker configi değil, **uygulama kodunun kullandığı konfigürasyondur**.

Yani:

```text
Docker Compose configi:
Container nasıl ayağa kalkacak?
Hangi port açılacak?
Hangi servis hangi container ile çalışacak?
```

```text
Spring uygulama configi:
Uygulama hangi DB'ye bağlanacak?
Kafka broker adresi ne olacak?
Eureka adresi ne olacak?
Datasource username/password ne olacak?
Aktif profil dev mi, test mi, prod mu?
```

Bu yüzden Docker Compose ile Config Server aynı şey değildir.

Config Server, uygulamanın çalışırken ihtiyaç duyduğu kodsal configleri merkezi olarak yönetir.

---

## 4. Neden Farklı Ortamlarda Farklı Configlere İhtiyaç Var?

Bir uygulama farklı ortamlarda farklı kaynaklara bağlanır.

Örneğin `product-service` lokal ortamda şu veritabanına bağlanabilir:

```text
jdbc:postgresql://localhost:5433/products
```

Ama test ortamında farklı bir DB olabilir:

```text
jdbc:postgresql://test.turkcell.com:5433/products
```

Prod ortamında ise bambaşka bir DB adresi olabilir:

```text
jdbc:postgresql://prod.turkcell.com:5433/products
```

Bu yüzden `application.yaml` dosyası uygulamanın çalıştığı ortama göre değişebilir.

---

## 5. Görseldeki Şema — Çoklu Ortam Mantığı

Derste çizilen ilk şemada şu fikir anlatıldı:

```text
12 değişken
12 x 3 = 36 konfigürasyon

Ortamlar:
local, test, prod
```

Her servis için farklı ortam dosyaları olabilir:

```text
application.yaml
application-test.yaml
application-prod.yaml
```

Bu şema bize şunu gösteriyor:

> Servis sayısı ve ortam sayısı arttıkça config dosyası sayısı da artar. Bu yüzden bu configleri merkezi bir yerden yönetmek gerekir.

---

## 6. Config Dosyaları Nerede Tutulabilir?

Derste görselde iki farklı seçenek yazıyordu:

```text
1. Dosya yolu: C:\Configs\
2. GitHub
```

Yani Config Server, config dosyalarını farklı kaynaklardan okuyabilir.

En çok kullanılan yöntemlerden biri configleri GitHub gibi bir Git repository içinde tutmaktır.

Bu derste configler GitHub repository üzerinden okutuldu.

---

## 7. Projede Config Klasörü Oluşturma

Derste `microservices` klasörünün altına `configs` klasörü açıldı.

Görselde şu yapı vardı:

```text
microservices
└── configs
```

Bu klasörün içinde servis bazlı config klasörleri oluşturuldu.

Örneğin:

```text
microservices
└── configs
    ├── application.yaml
    ├── product-service
    │   ├── application.yaml
    │   ├── application-dev.yaml
    │   ├── application-test.yaml
    │   └── application-prod.yaml
    └── user-service
```

Burada önemli mantık şudur:

- `configs/application.yaml` ortak configleri tutar.
- `configs/product-service/application.yaml` product-service'e ait ortak configleri tutar.
- `configs/product-service/application-dev.yaml` product-service'in dev ortamına ait configlerini tutar.
- `configs/product-service/application-test.yaml` test ortamı configlerini tutar.
- `configs/product-service/application-prod.yaml` prod ortamı configlerini tutar.

---

## 8. Görseldeki Product Service Config Klasörü

Görselde `configs/product-service` altında şu dosyalar görünüyordu:

```text
configs
└── product-service
    ├── application.dev.yaml
    ├── application.prod.yaml
    ├── application.test.yaml
    └── application.yaml
└── user-service
```

Dersteki mantık şu:

Product-service için genel ayarlar `application.yaml` içinde tutulabilir. Ortama göre değişen ayarlar ise dev, test ve prod dosyalarında tutulur.

> Not: Spring Cloud Config Server dosya isimlendirmesinde genelde `application-dev.yaml`, `application-test.yaml`, `application-prod.yaml` formatı kullanılır. Derste bazı yerlerde noktalı kullanım da görüldü. Projede hangi format kullanılıyorsa Config Server path ve profile mantığına göre ona uygun kullanılmalıdır.

---

## 9. Ortak Application Config Dosyası

Görselde `configs/application.yaml` dosyasında şu ortak config vardı:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

Bu dosya tüm servisler için ortak configleri tutar.

Bu örnekte actuator endpointlerinin tamamının dışarı açılması sağlanmıştır.

Yani bütün servislerde ayrı ayrı bunu yazmak yerine ortak `application.yaml` içine yazabiliriz.

---

## 10. Product Service Genel Config Dosyası

Görselde `configs/product-service/application.yaml` dosyasında şu kod vardı:

```yaml
spring:
  application:
    name: product-service
```

Bu dosya product-service'e ait genel configleri tutar.

Servisin adı burada tanımlanır.

Config Server bu servis adını kullanarak ilgili configleri bulur.

---

## 11. Product Service Ortam Bazlı Config Dosyaları

Derste görselde product-service için dev, test ve prod dosyaları açılmıştı.

### 11.1. Dev Ortamı

Görselde `application.dev.yaml` veya proje kullanımına göre `application-dev.yaml` dosyasında şu yapı vardı:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/products
    username: postgres
    password: test12345
    driver-class-name: org.postgresql.Driver
```

Bu dosya local/dev ortamı için veritabanı bağlantısını verir.

Buradaki anlam:

- Veritabanı localhost üzerinden çalışıyor.
- PostgreSQL portu dışarıdan `5433` ile erişiliyor.
- Database adı `products`.
- Kullanıcı adı `postgres`.
- Şifre `test12345`.

### 11.2. Test Ortamı

Görselde `application.test.yaml` veya proje kullanımına göre `application-test.yaml` dosyasında şu yapı vardı:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://test.turkcell.com:5433/products
    username: postgres
    password: turkcelltest
    driver-class-name: org.postgresql.Driver
```

Bu dosya test ortamında kullanılacak DB ayarlarını temsil eder.

### 11.3. Prod Ortamı

Görselde `application.prod.yaml` veya proje kullanımına göre `application-prod.yaml` dosyasında şu yapı vardı:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://prod.turkcell.com:5433/products
    username: postgres
    password: turkcellprod
    driver-class-name: org.postgresql.Driver
```

Bu dosya canlı/prod ortamındaki DB ayarlarını temsil eder.

> Kurumsal yapılarda gerçek prod şifreleri genellikle GitHub gibi açık repositorylerde tutulmaz. Bunun yerine Vault, Kubernetes Secret, environment variable veya cloud secret manager gibi yapılar kullanılır.

---

## 12. Config Server Projesi Oluşturma

Derste Config Server için ayrı bir Spring Boot projesi oluşturuldu.

Sonra:

1. Gereksiz dependencyler silindi.
2. Parent pom ayarı düzenlendi.
3. Parent `pom.xml` içine `config-server` module olarak eklendi.
4. Config Server dependencyleri eklendi.
5. Main class üzerine `@EnableConfigServer` eklendi.

---

## 13. Config Server Application YAML

Config Server'ın kendi `application.yaml` dosyası şu şekilde düzenlendi:

```yaml
spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://github.com/ayminacakir/turkcell-microservices-spring-gygy-5.git
          default-label: main
          search-paths: microservices/configs,microservices/configs/{application}
          clone-on-start: true
          force-pull: true

server:
  port: 8888
```

### Önemli Not

Derste not alınırken `search-pats` gibi yazılmış olabilir. Doğru property adı şudur:

Yanlış:

```yaml
search-pats
```

Doğru:

```yaml
search-paths
```

`search-paths`, Config Server'ın GitHub repository içinde hangi klasörlerde config arayacağını belirtir.

Bu örnekte:

```yaml
search-paths: microservices/configs,microservices/configs/{application}
```

anlamı şudur:

- Önce ortak configlerin bulunduğu `microservices/configs` klasörüne bak.
- Sonra servis bazlı configlerin bulunduğu `microservices/configs/{application}` klasörüne bak.

`{application}` kısmı, gelen servis adına göre değişir.

Örneğin istek şu ise:

```text
/product-service/dev
```

`{application}` yerine `product-service` gelir.

---

## 14. Config Server Main Class

Config Server uygulamasının main class'ı şu şekilde olmalıdır:

```java
package com.turkcell.config_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

Burada önemli annotation:

```java
@EnableConfigServer
```

Bu annotation Config Server özelliğini aktif eder.

Eğer import eksikse şu import eklenmelidir:

```java
import org.springframework.cloud.config.server.EnableConfigServer;
```

---

## 15. Config Server'ı Çalıştırma

Config Server'ı çalıştırmak için `microservices` klasöründe şu komut kullanıldı:

```bash
mvn spring-boot:run -pl config-server
```

Config Server ilk çalıştığında GitHub repository'yi belirli bir noktaya klonlar.

Sonra HTTP üzerinden configleri sunar.

---

## 16. Config Server'dan Config Okuma

Config Server çalıştıktan sonra şu adrese gidilir:

```text
http://localhost:8888/product-service/dev
```

Bu istek şu anlama gelir:

```text
Bana product-service uygulamasının dev profilindeki configlerini getir.
```

Config Server şu dosyaları birleştirerek dönebilir:

```text
microservices/configs/application.yaml
microservices/configs/product-service/application.yaml
microservices/configs/product-service/application-dev.yaml
```

Yani ortak config + servis configi + ortam configi birlikte sunulur.

---

## 17. Config Server Response Örneği

Derste beklenen örnek response şu şekildeydi:

```json
{
  "name": "product-service",
  "profiles": [
    "dev"
  ],
  "label": null,
  "version": "d64e6c442295c0812c94ed0cabac374e012ad42e",
  "state": "",
  "propertySources": [
    {
      "name": "https://github.com/ayminacakir/turkcell-microservices-spring-gygy-5.git/microservices/configs/product-service/application-dev.yaml",
      "source": {
        "spring.datasource.url": "jdbc:postgresql://localhost:5433/products",
        "spring.datasource.username": "postgres",
        "spring.datasource.password": "test12345",
        "spring.datasource.driver-class-name": "org.postgresql.Driver"
      }
    },
    {
      "name": "https://github.com/ayminacakir/turkcell-microservices-spring-gygy-5.git/microservices/configs/product-service/application.yaml",
      "source": {
        "spring.application.name": "product-service"
      }
    },
    {
      "name": "https://github.com/ayminacakir/turkcell-microservices-spring-gygy-5.git/microservices/configs/application.yaml",
      "source": {
        "management.endpoints.web.exposure.include": "*"
      }
    }
  ]
}
```

Bu response bize şunu söyler:

> Product-service dev ortamında çalışacaksa, kullanması gereken config değerleri bunlardır.

Burada 3 farklı config kaynağı vardır:

1. `application-dev.yaml`: Dev ortamı için datasource bilgileri.
2. `product-service/application.yaml`: Product-service'e ait genel bilgiler.
3. `configs/application.yaml`: Tüm servisler için ortak bilgiler.

---

## 18. Config Server'ın Sağladığı Avantajlar

Config Server sayesinde:

1. Configler online olarak değiştirilebilir.
2. Config yönetimi merkezileşir.
3. Her servisin içine aynı configleri tekrar tekrar yazmaya gerek kalmaz.
4. Ortama göre config değiştirmek kolaylaşır.
5. Servisler configlerini API gibi Config Server'dan alır.

Derste özellikle şu fikir vurgulandı:

> Artık kendine configürasyonları API gibi sunan bir sunucu elde ettin.

---

## 19. Secret Bilgiler Nerede Tutulmalı?

Derste şu soru soruldu:

> Kurumsal yapılarda staging ve prod gibi ortamlarda DB detayları gibi kritik bilgiler repo içerisinde tutulur mu?

Cevap:

Kritik bilgiler, özellikle şifreler ve secret değerler, GitHub gibi repositorylerde tutulmamalıdır.

Kurumsal yapılarda genellikle şu araçlar kullanılır:

- Vault
- Kubernetes Secret
- Environment variable
- Cloud secret manager
- CI/CD secret alanları

Eğer Kubernetes kullanılıyorsa, secret olan değişkenler Kubernetes tarafında yönetilebilir.

Bu durumda yazılım bazında ekstra bir şey yazmadan secret değerler ortama enjekte edilebilir.

---

## 20. Config Server Hazır, Peki Servisler Nasıl Config Okuyacak?

Config Server sadece configleri sunar.

Ama servislerin bu configleri okuyabilmesi için client tarafında da ayar gerekir.

Yani:

```text
Config Server:
Configleri sağlayan taraf.

Config Client:
Config Server'dan configleri okuyan servis.
```

Product-service, Config Server'dan config okuyacak hale getirildi.

---

## 21. Product Service Eski Application YAML

Product-service içinde önceden birçok config vardı:

```yaml
spring:
  application:
    name: product-service
  cloud:
    stream:
      bindings:
        testEvent-out-0:
          destination: test-topic # hangi başlığa bu eventi göndereyim?
          content-type: application/json # hangi tür ile göndereyim?
      kafka:
        binder:
          brokers: localhost:9092
  datasource:
    url: jdbc:postgresql://localhost:5433/products
    username: postgres
    password: test12345
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
```

Bu dosyada hem Kafka hem DB hem JPA ayarları bulunuyordu.

Dersin amacı bu ayarları servis içinden çıkartıp Config Server tarafına taşımaktı.

---

## 22. Product Service Configlerinin Config Server'a Taşınması

Product-service içindeki ortam bazlı ayarlar şu dosyaya taşındı:

```text
microservices/configs/product-service/application-dev.yaml
```

Dev config dosyası şu hale geldi:

```yaml
spring:
  cloud:
    stream:
      bindings:
        testEvent-out-0:
          destination: test-topic # hangi başlığa bu eventi göndereyim?
          content-type: application/json # hangi tür ile göndereyim?
      kafka:
        binder:
          brokers: localhost:9092
  datasource:
    url: jdbc:postgresql://localhost:5433/products
    username: postgres
    password: test12345
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
```

Burada artık product-service'in DB ve Kafka ayarları kendi servis klasöründen çıkıp merkezi config klasörüne taşınmış oldu.

---

## 23. Product Service Yeni Application YAML

Product-service içindeki `src/main/resources/application.yaml` dosyası sadeleştirildi.

Başlangıçta şöyle bırakıldı:

```yaml
spring:
  application:
    name: product-service

server:
  port: 8082
```

Daha sonra Config Server'dan config çekebilmesi için şu hale getirildi:

```yaml
spring:
  application:
    name: product-service
  profiles:
    active: dev
  config:
    import: optional:configserver:http://localhost:8888

server:
  port: 8082
```

Burada önemli alan:

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

Bu satır product-service'e şunu söyler:

```text
Config değerlerini localhost:8888 adresindeki Config Server'dan al.
```

---

## 24. Config Client Dependency

Config Server'dan config okuyacak servislerde config client dependency gerekir.

Product-service `pom.xml` dosyasına şu dependency eklendi:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-config-client</artifactId>
</dependency>
```

Bazı Spring Cloud sürümlerinde şu dependency de kullanılabilir:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

Derste kullanılan mantık:

```text
Config Server dependency:
Configleri sunan tarafta kullanılır.

Config Client dependency:
Configleri okuyan servislerde kullanılır.
```

---

## 25. Active Profile Mantığı

Product-service içinde şu satır vardır:

```yaml
spring:
  profiles:
    active: dev
```

Bu satır, uygulamanın hangi ortam configini okuyacağını belirler.

Örneğin:

```yaml
active: dev
```

yazılırsa Config Server şunu arar:

```text
product-service dev profile configleri
```

Yani örnek olarak:

```text
application-dev.yaml
```

Eğer şu yapılırsa:

```yaml
active: test
```

bu sefer test configleri okunur.

Eğer şu yapılırsa:

```yaml
active: prod
```

prod configleri okunur.

Bu yüzden sadece `active profile` değiştirerek uygulamanın bağlandığı ortam tamamen değiştirilebilir.

---

## 26. Optional Config Server Kullanımı

Product-service içinde şu kullanım vardı:

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

Buradaki `optional` şu anlama gelir:

> Config Server kapalı olsa bile servis tamamen patlamasın, localdeki configlerle çalışmaya devam edebilsin.

Eğer `optional` yazılmazsa ve Config Server kapalıysa servis başlamayabilir.

Yani:

```yaml
import: optional:configserver:http://localhost:8888
```

şu demektir:

```text
Config Server varsa oradan oku, yoksa uygulamayı tamamen durdurma.
```

---

## 27. Dikkat Edilmesi Gereken Yazım Hatası

Notlarda şu şekilde yazılmıştı:

```yaml
import: "optional:confgiserver:http://localhost:8888"
```

Burada yazım hatası vardır.

Yanlış:

```yaml
confgiserver
```

Doğru:

```yaml
configserver
```

Doğru kullanım:

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

Ayrıca YAML girintisi de çok önemlidir.

Yanlış kullanım:

```yaml
spring:
  config:
  import: optional:configserver:http://localhost:8888
```

Doğru kullanım:

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

---

## 28. Projeleri Başlatma Sırası

Config Server kullanmaya başladıktan sonra servisleri başlatma sırası önem kazandı.

Çünkü product-service ayağa kalkarken Config Server'a gidip config çekmeye çalışır.

Başlatma sırası şu şekilde olmalıdır:

```text
1. Config Server başlatılır.
2. Eureka Server başlatılır.
3. PostgreSQL / Kafka gibi altyapı servisleri çalışır durumda olur.
4. Product Service başlatılır.
5. Diğer servisler başlatılır.
```

Derste özellikle şu vurgulandı:

> Önce config-server'ı başlat. Çünkü sonradan başlatacağın product-service config-server'dan veri çekecek.

---

## 29. Product Service Neden DB Configi Olmadan Açıldı?

Product-service içindeki `application.yaml` dosyasında artık DB bağlantısı yazmamasına rağmen servis açılabildi.

Sebep:

Product-service şu ayar sayesinde Config Server'a gidiyor:

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

Sonra şu adresteki configleri alıyor:

```text
http://localhost:8888/product-service/dev
```

Buradan gelen değerler product-service için DB tanımını tamamlıyor.

Yani DB bilgisi artık product-service'in kendi içindeki `application.yaml` dosyasında değil, Config Server'ın sunduğu config dosyasında duruyor.

---

## 30. Config Server'dan Gelen Değerlerin DB Tanımını Tamamlaması

Config Server response içinde şu bilgiler product-service'e gelir:

```json
{
  "name": "product-service",
  "profiles": [
    "dev"
  ],
  "label": null,
  "version": "d64e6c442295c0812c94ed0cabac374e012ad42e",
  "state": "",
  "propertySources": [
    {
      "name": "https://github.com/ayminacakir/turkcell-microservices-spring-gygy-5.git/microservices/configs/product-service/application-dev.yaml",
      "source": {
        "spring.datasource.url": "jdbc:postgresql://localhost:5433/products",
        "spring.datasource.username": "postgres",
        "spring.datasource.password": "test12345",
        "spring.datasource.driver-class-name": "org.postgresql.Driver"
      }
    },
    {
      "name": "https://github.com/ayminacakir/turkcell-microservices-spring-gygy-5.git/microservices/configs/product-service/application.yaml",
      "source": {
        "spring.application.name": "product-service"
      }
    },
    {
      "name": "https://github.com/ayminacakir/turkcell-microservices-spring-gygy-5.git/microservices/configs/application.yaml",
      "source": {
        "management.endpoints.web.exposure.include": "*"
      }
    }
  ]
}
```

Bu değerler geldiği için product-service kendi içinde DB configi olmasa bile veritabanına bağlanabilir.

---

## 31. GitHub'a Push Etme Zorunluluğu

Config Server GitHub repository'den config okuduğu için config dosyalarında değişiklik yapınca GitHub'a göndermek gerekir.

Yani sadece localde değişiklik yapmak yetmez.

Derste vurgulanan cümle:

```text
Bir konfigürasyon değeri değiştiriyorsan GitHub'a göndermen şart, çünkü oradan çekiyor.
```

Kullanılabilecek komutlar:

```bash
git status
```

```bash
git add .
```

```bash
git commit -m "Configure services with config server"
```

```bash
git push origin main
```

---

## 32. IntelliJ / IDE Ortamını Düzenleme

Derste proje içinde bazı klasörlerin görünmesi rahatsız ettiği için exclude ayarı yapıldı.

Rahatsız eden klasör örnekleri:

```text
.mvn
target
.vscode
```

Settings üzerinden exclude pattern olarak şunlar eklendi:

```text
**/target
**/.vscode
**/.mvn
```

Bu sayede proje görünümü daha temiz hale getirildi.

---

## 33. Derste Karşılaşılan Önemli Hatalar ve Çözümleri

### 33.1. `@EnableConfigServer` Bulunamadı Hatası

Hata:

```text
cannot find symbol
symbol: class EnableConfigServer
```

Sebep:

- `spring-cloud-config-server` dependency eksik olabilir.
- `EnableConfigServer` importu eksik olabilir.

Çözüm:

Config Server `pom.xml` içinde şu dependency olmalı:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-config-server</artifactId>
</dependency>
```

Main class içinde şu import olmalı:

```java
import org.springframework.cloud.config.server.EnableConfigServer;
```

---

### 33.2. `propertySources` Boş Gelmesi

Eğer şu adrese gidildiğinde:

```text
http://localhost:8888/product-service/dev
```

şöyle boş response gelirse:

```json
{
  "name": "product-service",
  "profiles": [
    "dev"
  ],
  "label": null,
  "version": "...",
  "state": "",
  "propertySources": []
}
```

Bu Config Server'ın repository'yi gördüğünü ama dosyayı bulamadığını gösterir.

Olası sebepler:

1. `search-paths` yanlış yazılmıştır.
2. Config dosyası GitHub'a pushlanmamıştır.
3. Dosya yanlış klasördedir.
4. Dosya adı profile formatına uygun değildir.

Doğru config örneği:

```yaml
spring:
  cloud:
    config:
      server:
        git:
          search-paths: microservices/configs,microservices/configs/{application}
```

---

### 33.3. `spring.config` Bind Hatası

Hata:

```text
Failed to bind properties under 'spring.config'
ConverterNotFoundException: No converter found capable of converting from type [java.lang.String]
```


Yanlış:

```yaml
spring:
  config: optional:configserver:http://localhost:8888
```

Doğru:

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

YAML girintisi doğru olmalıdır.

---

### 33.4. PostgreSQL Connection Refused Hatası

Hata:

```text
Connection to localhost:5433 refused
```

Sebep:

Product-service veritabanına bağlanmaya çalışıyor ama `localhost:5433` üzerinde PostgreSQL çalışmıyor.

Çözüm:

Önce `product-db` container çalışıyor mu kontrol edilir:

```bash
docker ps
```

Çalışmıyorsa:

```bash
docker compose up -d product-db
```

veya:

```bash
docker compose up -d
```

Sonra tekrar product-service başlatılır:

```bash
mvn spring-boot:run -pl product-service
```

---

## 34. Product Service Başarılı Çalışma Logları

Product-service başarılı çalıştığında loglarda şu ifadeler görülebilir:

```text
Fetching config from server at : http://localhost:8888
Located environment: name=product-service, profiles=[dev]
```

Bu, servis Config Server'dan config çekti demektir.

Database bağlantısı başarılıysa şu tarz log görülür:

```text
Database JDBC URL [jdbc:postgresql://localhost:5433/products]
Database dialect: PostgreSQLDialect
```

Eureka kaydı başarılıysa şu tarz log görülür:

```text
Registering application PRODUCT-SERVICE with eureka with status UP
registration status: 204
```

Uygulama başarılı başladıysa:

```text
Started ProductServiceApplication
```

---

## 35. Kontrol Edilecek Adresler

Config Server product-service dev config kontrolü:

```text
http://localhost:8888/product-service/dev
```

Product-service health kontrolü:

```text
http://localhost:8082/actuator/health
```

Eureka paneli:

```text
http://localhost:8761
```

Eureka panelinde `PRODUCT-SERVICE` görünmelidir.

---

## 36. Ödev 1

Derste verilen ilk ödev:

```text
Order-service ile product-service arasında gerçek bir sipariş akışı event iletişimi kurulacak.
```

Bu ödevde amaç:

- Order-service sipariş oluşturduğunda event yayınlayacak.
- Product-service bu eventi dinleyecek.
- Servisler arası doğrudan bağımlılık yerine event-driven iletişim kurulacak.
- Kafka üzerinden gerçek sipariş akışı sağlanacak.

---

## 37. Ödev 2

Derste verilen ikinci ödev:

```text
Bütün servislerin config-server ile iletişime geçmesi sağlanacak.
Bütün konfigürasyonların config-server'dan gelmesi sağlanacak.
Projelerin hiçbirinde config-server ve port özellikleri dışında konfigürasyon kalmayacak.
```

Bu ödevde yapılması gerekenler:

1. Her servis için config klasörü oluşturulmalı.
2. Servislere ait ortam bazlı configler `microservices/configs` altına taşınmalı.
3. Her servisin kendi `application.yaml` dosyasında sadece minimum bilgiler kalmalı.
4. Her servise config client dependency eklenmeli.
5. Her servis `spring.config.import` ile Config Server'a bağlanmalı.
6. Config Server'dan gelen değerlerle servislerin çalıştığı test edilmeli.

Örnek servis içi minimum config:

```yaml
spring:
  application:
    name: product-service
  profiles:
    active: dev
  config:
    import: optional:configserver:http://localhost:8888

server:
  port: 8082
```

---

## 38. Genel Akış Özeti

Bu derste yapılan işlem akışı şöyleydi:

```text
1. Config Server ihtiyacı anlatıldı.
2. Çoklu ortam config problemi açıklandı.
3. microservices/configs klasörü oluşturuldu.
4. Ortak application.yaml dosyası oluşturuldu.
5. Servis bazlı config klasörleri oluşturuldu.
6. Product-service için application.yaml, application-dev.yaml, application-test.yaml, application-prod.yaml mantığı anlatıldı.
7. Config Server projesi oluşturuldu.
8. Config Server application.yaml dosyası düzenlendi.
9. @EnableConfigServer eklendi.
10. Config Server çalıştırıldı.
11. http://localhost:8888/product-service/dev adresinden configler test edildi.
12. Product-service içindeki configler merkezi config klasörüne taşındı.
13. Product-service config client haline getirildi.
14. Product-service spring.config.import ile Config Server'a bağlandı.
15. Product-service başlatıldı ve Config Server'dan config çektiği doğrulandı.
16. DB bağlantısı ve Eureka kaydı kontrol edildi.
```

---

## 39. Akılda Kalıcı Kısa Özet

Config Server şu işe yarar:

```text
Mikroservislerin configlerini tek merkezden yönetir.
```

Normalde:

```text
Her servis kendi application.yaml dosyasından okur.
```

Config Server ile:

```text
Her servis Config Server'a gider ve kendi ortamına uygun configleri oradan alır.
```

Product-service örneğinde:

```text
product-service + dev profile
```

şu adrese gider:

```text
http://localhost:8888/product-service/dev
```

Config Server da şu dosyaları birleştirip döner:

```text
configs/application.yaml
configs/product-service/application.yaml
configs/product-service/application-dev.yaml
```

Böylece product-service içinde DB, Kafka, JPA gibi ayarları tutmaya gerek kalmaz.

---

## 40. En Önemli Kodlar

### Config Server `application.yaml`

```yaml
spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://github.com/ayminacakir/turkcell-microservices-spring-gygy-5.git
          default-label: main
          search-paths: microservices/configs,microservices/configs/{application}
          clone-on-start: true
          force-pull: true

server:
  port: 8888
```

### Config Server Main Class

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

### Product Service Config Client `application.yaml`

```yaml
spring:
  application:
    name: product-service
  profiles:
    active: dev
  config:
    import: optional:configserver:http://localhost:8888

server:
  port: 8082
```

### Product Service `application-dev.yaml`

```yaml
spring:
  cloud:
    stream:
      bindings:
        testEvent-out-0:
          destination: test-topic
          content-type: application/json
      kafka:
        binder:
          brokers: localhost:9092
  datasource:
    url: jdbc:postgresql://localhost:5433/products
    username: postgres
    password: test12345
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
```

### Ortak Config `configs/application.yaml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

### Config Server Çalıştırma

```bash
mvn spring-boot:run -pl config-server
```

### Product Service Çalıştırma

```bash
mvn spring-boot:run -pl product-service
```

### Config Kontrol URL'i

```text
http://localhost:8888/product-service/dev
```

---

## 41. Sonuç

Bu derste mikroservislerde merkezi config yönetimi öğrenildi.

Önceden her servis kendi içinde DB, Kafka, JPA gibi ayarları tutuyordu. Bu yapı servis sayısı ve ortam sayısı arttıkça zor yönetilir hale geliyordu.

Config Server ile bu ayarlar merkezi bir GitHub repository altında toplandı. Servisler de Config Client olarak Config Server'a bağlanıp kendi configlerini oradan okuyacak hale getirildi.

Bu sayede:

- Config yönetimi merkezileşti.
- Ortama göre config değiştirmek kolaylaştı.
- Servislerin kendi içindeki configler sadeleşti.
- Configler API gibi dışarı sunuldu.
- Product-service'in DB ve Kafka ayarlarını Config Server'dan okuyabildiği görüldü.

