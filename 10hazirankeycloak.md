# 10 Haziran Dersi - Keycloak, OAuth2 Resource Server ve JWT Güvenliği

Bu not, `10 haziran.docx` içeriği ve görselleri temel alınarak tekrar için sadeleştirilmiştir. Tekrar eden açıklamalar birleştirildi; aynı kavramlar farklı başlıklarda tekrar anlatılmadı.

---

## 1. Dersin Ana Fikri

Mikroservis mimarisinde birçok servis vardır:

```text
product-service
user-service
order-service
gateway
notification-service
...
```

Bu servislerin her birinde ayrı ayrı login, şifre kontrolü, token üretimi ve rol yönetimi yazmak doğru değildir. Çünkü kod tekrarı olur, güvenlik hatası ihtimali artar ve auth mantığını güncellemek zorlaşır.

Bu yüzden derste şu fikir anlatıldı:

> Tekerleği yeniden icat etmeye gerek yok.

Authentication ve authorization işlemleri merkezi bir sisteme bırakılır. Bu derste bu merkezi sistem **Keycloak** oldu.

```text
Keycloak = Identity Server + Authorization Server + IAM sistemi
```

Mikroservisler ise şu role getirildi:

```text
product-service / user-service / order-service / gateway = Resource Server
```

Yani genel akış şudur:

```text
1. Kullanıcı Keycloak'a username/password gönderir.
2. Keycloak kullanıcıyı doğrular.
3. Keycloak access token üretir.
4. Kullanıcı bu token ile mikroservise istek atar.
5. Mikroservis token'ı doğrular.
6. Token geçerliyse endpoint çalışır, yoksa 401 döner.
```

---

## 2. Temel Kavramlar

### Authentication

Kullanıcının kim olduğunu doğrulamaktır.

```text
Bu kişi gerçekten user1 mi?
Şifresi doğru mu?
```

Kısaca:

```text
Authentication = Kimlik doğrulama
```

### Authorization

Doğrulanmış kullanıcının neye erişebileceğini belirlemektir.

```text
Bu kullanıcı ürünleri görebilir mi?
Admin endpoint'ine girebilir mi?
Sipariş silebilir mi?
```

Kısaca:

```text
Authorization = Yetkilendirme
```

### IAM

IAM, `Identity and Access Management` demektir.

Türkçesi:

```text
Kimlik ve erişim yönetimi
```

Kullanıcı, rol, yetki, client, login ve token yönetimini kapsar.

### Authorization Server

Kullanıcıyı doğrulayan ve token üreten sistemdir.

Bu derste:

```text
Authorization Server = Keycloak
```

### Resource Server

Kendi endpoint'lerini koruyan ve gelen token'ı doğrulayan servistir.

Bu derste:

```text
Resource Server = product-service, user-service, order-service, gateway
```

Resource Server kullanıcı şifresini bilmez. Sadece gelen JWT geçerli mi diye kontrol eder.

### JWT

JWT, `JSON Web Token` demektir. İçinde kullanıcı ve token bilgileri taşır. İmzalıdır.

API isteklerinde genellikle şöyle gönderilir:

```http
Authorization: Bearer <access_token>
```

### Stateless

Server'ın kullanıcı session bilgisi tutmamasıdır. Her request kendi token'ı ile gelir.

```text
Session server'da tutulmaz.
Her istek Authorization header içindeki token ile doğrulanır.
```

JWT'nin stateless olması mimari avantajdır; tek başına güvenlik sebebi değildir. Token çalınırsa süresi bitene kadar kullanılabilir.

---

## 3. Keycloak Docker Compose Ayarı

Derste Docker Compose içine Keycloak servisi eklendi:

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:26.1
  container_name: keycloak
  restart: unless-stopped
  command: start-dev --import-realm
  environment:
    KC_BOOTSTRAP_ADMIN_USERNAME: admin
    KC_BOOTSTRAP_ADMIN_PASSWORD: admin
    KC_HOSTNAME_STRICT: "false"
    KC_HTTP_ENABLED: "true"
  ports:
    - "8087:8080"
  networks:
    - kafka-net
```

### Satırların Mantığı

| Kod | Açıklama |
|---|---|
| `image` | Kullanılacak Keycloak Docker image'ı. |
| `container_name` | Container adını `keycloak` yapar. |
| `restart: unless-stopped` | Elle durdurulmadıkça hata olursa tekrar başlatır. |
| `command: start-dev --import-realm` | Development modunda başlatır, varsa realm import eder. |
| `KC_BOOTSTRAP_ADMIN_USERNAME` | Admin kullanıcı adını belirler. |
| `KC_BOOTSTRAP_ADMIN_PASSWORD` | Admin şifresini belirler. |
| `KC_HOSTNAME_STRICT: false` | Development ortamında hostname kontrolünü esnetir. |
| `KC_HTTP_ENABLED: true` | HTTP ile çalışmasına izin verir. |
| `8087:8080` | Bilgisayarda 8087 portu, container içinde 8080 portuna gider. |
| `kafka-net` | Keycloak'u mevcut Docker network'e bağlar. |

Çalıştırma:

```bash
docker compose down
docker compose up -d
```

Sonra Keycloak'a şu adresten girilir:

```text
http://localhost:8087/
```

Admin girişi:

```text
username: admin
password: admin
```

---

## 4. Realm, User, Role ve Client

### Realm

Realm, Keycloak içinde izole edilmiş bir auth dünyasıdır.

Örnek:

```text
pasaj realm -> kendi user, role, client yapısı
crm realm   -> kendi user, role, client yapısı
```

Bir realm'deki kullanıcı ve roller başka realm'i etkilemez.

Derste oluşturulan realm:

```text
microservices-gygy5
```

Mail sunucusu benzetmesi:

```text
Bir mail sağlayıcısı alırsın.
Her çalışan için ayrı mail sunucusu almazsın.
Aynı sağlayıcı içinde farklı hesaplar açarsın.
```

Keycloak da böyledir:

```text
Tek Keycloak sunucusu
├── microservices-gygy5 realm
├── crm realm
└── pasaj realm
```

### User

Realm içindeki kullanıcıdır.

Derste örnek kullanıcı oluşturuldu:

```text
username: user1
FirstName: User
LastName: User
password: 123456
```

Şifre `Credentials` sekmesinden belirlenir.

### Role

Kullanıcıya verilen yetki adıdır.

Örnek roller:

```text
USER
ADMIN
CUSTOMER
SUPPORT
```

### Client

Keycloak'u kullanmasına izin verilen uygulamadır.

Örnek:

```text
gygy5-public
frontend-app
gateway-client
mobile-app
```

Her gelen uygulama senin realm'ini kullanamaz. Önce client olarak tanımlanması gerekir.

Derste oluşturulan client:

```text
Client type: OpenID Connect
Client ID: gygy5-public
```

### OpenID Connect

OAuth2 üzerine kimlik doğrulama ekleyen standarttır.

```text
OAuth2 -> Yetkilendirme
OpenID Connect -> OAuth2 + kimlik doğrulama
```

---

## 5. Client Ayarları

Derste client oluştururken bazı ayarlar yapıldı.

### Client Authentication

Kapalı bırakıldı.

```text
Client authentication: Off
```

Sebep: Bu client public client olarak kullanıldı. Public client'ta client secret bulunmaz.

```text
Public client       -> client secret yok
Confidential client -> client secret var
```

### Direct Access Grants

Açık bırakıldı.

```text
Direct access grants: On
```

Bu ayar Postman'de username/password ile token almayı sağlar.

Postman body örneği:

```text
grant_type=password
username=user1
password=123456
client_id=gygy5-public
```

Bu yöntem test için pratiktir. Gerçek frontend uygulamalarında genellikle Authorization Code Flow daha uygundur.

### Valid Redirect URIs ve Web Origins

Development ortamında şu verildi:

```text
Valid redirect URIs: *
Web origins: *
```

Bu, her yerden yönlendirme ve browser isteğine izin verir. Geliştirme ortamında kolaylık sağlar ama production için güvenli değildir.

Production'da daha doğru kullanım:

```text
Valid redirect URIs: https://frontend.domain.com/*
Web origins: https://frontend.domain.com
```

---

## 6. Postman ile Token Alma

Token almak için Postman'de şu endpoint'e POST isteği atıldı:

```http
POST http://localhost:8087/realms/microservices-gygy5/protocol/openid-connect/token
```

Body tipi:

```text
x-www-form-urlencoded
```

Body değerleri:

```text
grant_type: password
username: user1
password: 123456
client_id: gygy5-public
```

Cevapta gelen önemli alanlar:

```json
{
  "access_token": "...",
  "expires_in": 300,
  "refresh_token": "...",
  "refresh_expires_in": 1800,
  "token_type": "Bearer"
}
```

### Access Token

API'lere erişmek için kullanılan kısa ömürlü tokendir.

```http
Authorization: Bearer <access_token>
```

### Refresh Token

Access token süresi dolunca yeni access token almak için kullanılır.

Refresh token daha kritik bir bilgidir. Çünkü yeni access token üretmeye yarar. Bu yüzden güvenli saklanmalıdır.

### Bearer Token

`Bearer`, “bu token'ı taşıyan kişi bu isteği yapabilir” mantığıdır. Bu yüzden token çalınırsa risk oluşur.

---

## 7. Product Service'i Resource Server Yapmak

Derste amaç şuydu:

```text
Kullanıcı login olmadan product-service endpoint'ine istek atamasın.
Token yoksa 401 dönsün.
Token varsa endpoint çalışsın.
```

Örnek endpoint:

```java
@RestController
@RequestMapping("/api/v1/products")
public class ProductsController {

    @GetMapping
    public String hello() {
        return "Hello";
    }
}
```

Security eklenmeden önce:

```text
GET http://localhost:8082/api/v1/products -> Hello
```

Security eklendikten sonra:

```text
Token yoksa -> 401 Unauthorized
Token varsa -> 200 OK / Hello
```

---

## 8. Gerekli Dependency

Product-service'e şu dependency eklendi:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

Bu bağımlılık uygulamayı OAuth2 Resource Server yapar. Yani gelen JWT token'ı doğrulayabilir.

Kontrol için:

```bash
mvn clean compile
```

---

## 9. application-dev.yaml Ayarı

Config dosyasına issuer bilgisi eklendi:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8087/realms/microservices-gygy5
```

### issuer-uri Nedir?

Resource Server'a şunu söyler:

```text
JWT token'ları bu Keycloak realm'ine göre doğrula.
```

Spring Security bu adres üzerinden Keycloak'un public key bilgilerine ulaşır ve token imzasını doğrular.

> Dikkat: Keycloak Docker'da `8087:8080` ile çalışıyorsa issuer-uri içinde port da 8087 olmalıdır. Eğer 8085 yazılırsa doğrulama hatası alınabilir.

---

## 10. SecurityConfig Kodu ve Mantığı

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/actuator/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                ).permitAll()
                .anyRequest().authenticated()
        );

        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
```

### Kodun Kısa Mantığı

```text
/actuator, /swagger-ui ve /v3/api-docs açık kalsın.
Diğer tüm endpoint'ler token istesin.
Gelen JWT doğrulansın.
CSRF kapatılsın.
Session oluşturulmasın, stateless çalışılsın.
```

### Önemli Satırlar

| Kod | Anlamı |
|---|---|
| `@Configuration` | Spring'e bu sınıfın config sınıfı olduğunu söyler. |
| `@EnableWebSecurity` | Spring Security'yi aktif eder. |
| `SecurityFilterChain` | HTTP güvenlik kurallarını tanımlar. |
| `permitAll()` | Belirtilen endpoint'lere token olmadan izin verir. |
| `anyRequest().authenticated()` | Diğer tüm isteklerde authentication ister. |
| `oauth2ResourceServer(...jwt...)` | Uygulamayı JWT doğrulayan Resource Server yapar. |
| `csrf(...disable)` | CSRF korumasını kapatır. |
| `STATELESS` | Server-side session oluşturmaz. |

---

## 11. Test Akışı

### 1. Token Almadan İstek

```http
GET http://localhost:8082/api/v1/products
```

Beklenen sonuç:

```text
401 Unauthorized
```

### 2. Keycloak'tan Token Al

```http
POST http://localhost:8087/realms/microservices-gygy5/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
username=user1
password=123456
client_id=gygy5-public
```

Beklenen sonuç:

```text
access_token döner
```

### 3. Bearer Token ile İstek

```http
GET http://localhost:8082/api/v1/products
Authorization: Bearer <access_token>
```

Beklenen sonuç:

```text
200 OK
Hello
```

---

## 12. Derste Sorulan Sorular ve Cevapları

### Soru 1: Debezium outbox ayarlarını tekrar eklemeli miyim?

Aynı ayarı iki kez eklemeye gerek yoktur. Ama uygulamanın görevi ile Debezium'un görevi karıştırılmamalıdır.

```text
Outbox tablosuna event yazmak -> uygulamanın görevi
Outbox tablosunu Kafka'ya aktarmak -> Debezium'un görevi
```

Debezium connector'da zaten tanımlı transform veya routing ayarlarını tekrar tekrar eklemek gerekmez.

---

### Soru 2: Logout olduktan sonra yönlendirme neden Keycloak üzerinden yapılıyor?

Çünkü login ve oturum yönetimini Keycloak yapar. Sadece frontend'deki token'ı silmek yeterli olmayabilir. Keycloak tarafındaki SSO oturumunun da kapatılması gerekir.

Akış:

```text
1. Kullanıcı logout'a basar.
2. Uygulama Keycloak logout endpoint'ine gider.
3. Keycloak kendi oturumunu sonlandırır.
4. Kullanıcı izin verilen logout redirect adresine yönlendirilir.
```

---

### Soru 3: JWT'nin güvenli olmasının sebebi stateless olması mı?

Stateless olması doğrudan güvenlik sebebi değildir; daha çok mimari avantajdır.

JWT güvenliği şu konulara bağlıdır:

```text
Token'ın nerede saklandığı
Access token süresinin kısa tutulması
Refresh token'ın güvenli saklanması
HTTPS kullanılması
Token imzasının doğru doğrulanması
```

Token çalınırsa süresi bitene kadar kullanılabilir. Bu yüzden token saklama yeri çok önemlidir.

---

### Soru 4: CSRF disable etmek güvenli mi?

JWT Authorization header ile gönderiliyorsa CSRF riski düşüktür. Çünkü browser başka bir siteye giderken Authorization header'ı otomatik göndermez.

Bu yüzden stateless REST API'lerde genellikle CSRF kapatılır.

Ama JWT cookie içinde tutuluyorsa durum değişir. Çünkü cookie browser tarafından otomatik gönderilebilir. Bu durumda CSRF tekrar önem kazanır.

Cookie tabanlı kullanımda şunlara dikkat edilmelidir:

```text
HttpOnly
Secure
SameSite
CSRF token
BFF mimarisi
```

---

### Soru 5: CSRF nedir?

CSRF, `Cross-Site Request Forgery` demektir.

Türkçesi:

```text
Siteler arası istek sahteciliği
```

Örnek:

```text
Kullanıcı bankaya giriş yapmıştır.
Session cookie browser'da duruyordur.
Kullanıcı kötü niyetli siteye girer.
O site browser'a banka sitesine istek attırır.
Browser cookie'yi otomatik gönderdiği için banka isteği gerçek sanabilir.
```

---

### Soru 6: Cookie nedir?

Cookie, browser'ın sakladığı küçük veri parçasıdır.

Örnek:

```text
session_id=abc123
```

Browser aynı domain'e istek atarken cookie'yi otomatik gönderebilir. Bu yüzden cookie tabanlı sistemlerde CSRF dikkate alınmalıdır.

---

### Soru 7: Refresh token ile yeni access token alma logic'i bize mi ait?

Hem Keycloak ayarlarına hem uygulama akışına bağlıdır.

Keycloak tarafı şunları belirler:

```text
Access token süresi
Refresh token süresi
Session süresi
Refresh token kullanılabilirliği
```

Frontend veya BFF tarafı ise şu akışı yönetir:

```text
1. Access token ile API'ye istek atılır.
2. Access token süresi dolarsa 401 alınır.
3. Refresh token ile Keycloak'tan yeni access token alınır.
4. İstek yeni token ile tekrar denenir.
```

---

### Soru 8: Gateway varsa servisleri ayrıca korumaya gerek var mı?

Evet, vardır.

Sadece gateway'i korumak yeterli değildir. Çünkü servisler yanlışlıkla dışarı açılabilir veya iç ağdan doğrudan erişilebilir.

Doğru yaklaşım:

```text
Gateway de korunur.
Her mikroservis de Resource Server olarak korunur.
```

Bu mantığa `defense in depth` denir. Yani tek güvenlik katmanına güvenmek yerine birden fazla güvenlik katmanı kurulur.

---

### Soru 9: Frontend'de JWT saklamak neden riskli?

Frontend tarafında token saklamak risklidir.

```text
localStorage -> JavaScript okuyabilir, XSS varsa token çalınabilir.
sessionStorage -> JavaScript okuyabilir.
Cookie -> Otomatik gönderildiği için CSRF riski olabilir.
```

Bu yüzden hoca “frontend'e JWT'nin asla sızmaması lazım” vurgusunu yaptı.

---

### Soru 10: BFF bu sorunu çözer mi?

BFF, `Backend for Frontend` demektir. Frontend ile mikroservisler arasında duran özel backend katmanıdır.

Akış:

```text
Frontend -> BFF
BFF -> Keycloak ile token yönetir
BFF -> Mikroservislere token ile istek atar
```

BFF sayesinde access token frontend tarafında tutulmak zorunda kalmaz. Bu güvenliği artırır. Ama BFF kullanırken de cookie, CSRF, HTTPS ve session ayarları doğru yapılmalıdır.

---

## 13. Ödev: Tüm Servislere OAuth2 Resource Server Eklemek

Ödevin amacı sadece product-service'i değil, tüm servisleri JWT ile korumaktır.

Örnek servisler:

```text
product-service
user-service
order-service
gateway
notification-service
```

Her servis için yapılacaklar:

```text
1. spring-boot-starter-oauth2-resource-server dependency ekle.
2. application-dev.yaml içine issuer-uri ekle.
3. SecurityConfig sınıfı yaz.
4. Public kalacak endpoint'leri belirle.
5. Diğer endpoint'leri authenticated yap.
6. Token yokken 401 döndüğünü test et.
7. Bearer token ile 200 döndüğünü test et.
```

---

## 14. Kısa Tekrar Akışı

```text
1. Docker Compose'a Keycloak eklendi.
2. docker compose down ve docker compose up -d çalıştırıldı.
3. http://localhost:8087 adresinden Keycloak'a girildi.
4. microservices-gygy5 realm oluşturuldu.
5. user1 kullanıcısı oluşturuldu.
6. Kullanıcıya 123456 şifresi verildi.
7. gygy5-public client oluşturuldu.
8. Direct access grants açıldı.
9. Postman ile Keycloak'tan access token alındı.
10. Product-service'e oauth2 resource server dependency eklendi.
11. application-dev.yaml içine issuer-uri yazıldı.
12. SecurityConfig ile endpoint'ler korundu.
13. Token olmadan istek atıldığında 401 alındı.
14. Bearer token ile istek atıldığında Hello cevabı alındı.
```

---

## 15. Mini Sözlük

| Kavram | Kısa Anlamı |
|---|---|
| Keycloak | Kullanıcı, rol, client ve token yöneten IAM / Authorization Server. |
| Realm | Keycloak içinde bağımsız auth dünyası. |
| Client | Keycloak'u kullanmasına izin verilen uygulama. |
| User | Realm içindeki kullanıcı. |
| Role | Kullanıcıya verilen yetki adı. |
| OAuth2 | Yetkilendirme standardı. |
| OpenID Connect | OAuth2 üzerine kimlik doğrulama ekleyen standart. |
| JWT | İmzalı JSON token. |
| Access Token | API'lere erişmek için kullanılan kısa ömürlü token. |
| Refresh Token | Yeni access token almak için kullanılan token. |
| Bearer Token | Authorization header ile taşınan token tipi. |
| Resource Server | Token doğrulayıp endpoint'lerini koruyan servis. |
| Authorization Server | Kullanıcıyı doğrulayıp token üreten sistem. |
| IAM | Kimlik ve erişim yönetimi. |
| SSO | Tek girişle birden fazla uygulamaya erişme. |
| LDAP | Kurumsal kullanıcı dizin sistemi. |
| Active Directory | Microsoft'un kurumsal kullanıcı ve domain yönetim sistemi. |
| CORS | Farklı origin'lerden gelen browser istekleri için izin mekanizması. |
| CSRF | Browser'a kullanıcının istemediği istekleri attırma saldırısı. |
| Cookie | Browser'ın sakladığı ve otomatik gönderebildiği küçük veri. |
| Stateless | Server'ın kullanıcı session'ı tutmaması. |
| 401 | Kullanıcı doğrulanmamış. Token yok veya geçersiz. |
| 403 | Kullanıcı doğrulanmış ama yetkisi yok. |
| BFF | Backend for Frontend; token yönetimini frontend yerine backend yapar. |

---

## 16. En Kısa Özet

```text
Keycloak merkezi Authorization Server'dır.
Kullanıcı Keycloak'tan JWT access token alır.
Mikroservisler Resource Server olur.
Mikroservisler gelen JWT'yi doğrular.
Token yoksa 401 döner.
Token geçerliyse endpoint çalışır.
Gateway olsa bile servislerin kendi güvenliği olmalıdır.
Frontend'de token saklamak risklidir.
BFF bu riski azaltabilir.
```
