# 12 Haziran Dersi Tekrar Notları  
## BFF Server, Keycloak, Session, SSO, CSRF ve Frontend Auth Akışı

---

# 1. Derste Ne Yapıldı?

Bu derste mikroservis projesine **BFF Server** eklendi.

BFF açılımı:

```text
Backend For Frontend
```

Bizim projedeki BFF’in amacı şudur:

```text
Frontend ile Gateway arasına güvenli bir sunucu koymak.
```

Yani normalde frontend direkt gateway’e istek atabilirdi:

```text
Frontend → Gateway → Product Service
```

Ama bu durumda frontend tarafında access token tutulması gerekebilirdi. Bu da güvenlik açısından risklidir.

Bu yüzden araya BFF koyduk:

```text
Frontend → BFF Server → Gateway Server → Product Service
```

Buradaki temel fikir:

```text
Token frontend'e verilmez.
Token BFF tarafında session içinde tutulur.
Frontend sadece session cookie ile çalışır.
```

---

# 2. Neden BFF Kullanıyoruz?

Hocanın anlattığı en önemli cümlelerden biri şuydu:

```text
Client'a cevap olarak giden her şey client'ındır.
Senin güvenlik mekanizmanın dışına çıkmıştır.
```

Yani frontend’e bir token gönderirsen artık o token:

- Browser’da görülebilir.
- LocalStorage’a yazılabilir.
- Network tabında görülebilir.
- XSS açığı varsa çalınabilir.
- Kullanıcı tarafında manipüle edilebilir.

Bu yüzden güvenlik açısından daha iyi olan yapı:

```text
Access token frontend'e hiç gitmesin.
Access token BFF server tarafında kalsın.
Frontend sadece cookie ile oturum taşısın.
```

---

# 3. BFF’in Görevi Nedir?

BFF’in görevi şudur:

```text
Kullanıcı login olmak istediğinde onu Keycloak'a yönlendirir.
Login başarılı olursa tokenları kendisi alır.
Tokenları frontend'e vermez.
Tokenları session içinde saklar.
Frontend'den gelen /api/** isteklerini Gateway'e yönlendirir.
Gateway'e giderken access token'ı Authorization header olarak ekler.
```

Yani BFF bir çeşit güvenli aracı sunucudur.

Akış şu şekildedir:

```text
1. Kullanıcı frontend üzerinden /api/v1/products isteği atar.
2. BFF kullanıcının login olup olmadığını kontrol eder.
3. Login yoksa kullanıcıyı Keycloak login ekranına yönlendirir.
4. Kullanıcı Keycloak'ta giriş yapar.
5. Keycloak BFF'e authorization code döner.
6. BFF bu code ile token alır.
7. Token frontend'e verilmez, BFF session içinde tutar.
8. Kullanıcı tekrar /api/v1/products isteğine devam eder.
9. BFF bu isteği Gateway'e yollar.
10. TokenRelay access token'ı Authorization header olarak Gateway'e ekler.
11. Gateway isteği ilgili mikroservise yönlendirir.
```

---

# 4. Keycloak’ta Neden Yeni Client Oluşturduk?

Keycloak tarafında yeni bir client oluşturuldu:

```text
client-id: bff-client
```

Bu client’ın tipi:

```text
OpenID Connect
```

Çünkü Spring Security OAuth2 Client ile Keycloak arasında OIDC üzerinden login yapılır.

Keycloak client ayarlarında önemli noktalar:

```text
Client authentication: ON
Authentication flow: Standard flow
Implicit flow: OFF
Direct access grants: OFF
Service accounts roles: OFF
```

---

# 5. Fotoğraf Açıklaması: Keycloak Client Ayarları

## Sayfa 2’deki Keycloak görseli

Bu görselde Keycloak üzerinde `bff-client` oluşturulduğu görülüyor.

Önemli ayarlar:

```text
Client ID: bff-client
Name: bff-client
Client authentication: ON
Standard flow: ON
```

Burada `Client authentication ON` yapılmasının sebebi şudur:

BFF server güvenilir backend tarafında çalışan bir uygulamadır. Bu yüzden Keycloak’a giderken sadece `client-id` değil, ayrıca `client-secret` da kullanabilir.

Yani BFF, Keycloak’a kendini şöyle tanıtır:

```text
Ben bff-client adlı güvenilir backend uygulamasıyım.
İşte benim client secret değerim.
Bana authorization code karşılığında token ver.
```

Frontend uygulamaları genelde public client olur. Çünkü frontend içinde secret güvenli saklanamaz.

Ama BFF backend olduğu için confidential client gibi davranabilir.

Bu yüzden:

```text
Frontend → secret tutamaz
BFF → secret tutabilir
```

---

# 6. Redirect URI Nedir?

Keycloak client ayarlarında şu alan önemliydi:

```text
Valid redirect URIs
```

Örneğin:

```text
http://localhost:9000/login/oauth2/code/keycloak
```

Bu URI şunu söyler:

```text
Kullanıcı Keycloak'ta login olduktan sonra nereye dönecek?
```

Spring Security OAuth2 Login varsayılan olarak şu endpoint’i kullanır:

```text
/login/oauth2/code/{registrationId}
```

Bizde registrationId şu:

```yaml
registration:
  keycloak:
```

Bu yüzden dönüş adresi:

```text
http://localhost:9000/login/oauth2/code/keycloak
```

olur.

---

# 7. BFF Application YAML Mantığı

BFF içinde şu ayarlar vardı:

```yaml
spring:
  application:
    name: bff-server

  profiles:
    active: dev

  config:
    import: optional:configserver:http://localhost:8888

  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: bff-client
            client-secret: ...
            authorization-grant-type: authorization_code
            scope: openid, profile, email
        provider:
          keycloak:
            issuer-uri: http://localhost:8087/realms/microservices-gygy5
```

Bu ayarların anlamı:

```text
spring.application.name
```

Servisin adıdır. Eureka ve Config Server tarafında bu isimle görünür.

```text
spring.profiles.active
```

Aktif profilin `dev` olduğunu söyler.

```text
spring.config.import
```

Config Server’dan ayar çekmek için kullanılır.

```text
client-id
```

Keycloak’ta oluşturduğumuz client’ın adıdır.

```text
client-secret
```

Keycloak client’ın gizli anahtarıdır.

```text
authorization-grant-type: authorization_code
```

Login akışında Authorization Code Flow kullanılacağını söyler.

```text
issuer-uri
```

Keycloak realm adresidir.

Bizde Keycloak Docker’da 8087 portundan çalıştığı için doğru adres:

```text
http://localhost:8087/realms/microservices-gygy5
```

---

# 8. Authorization Code Flow Nedir?

Authorization Code Flow şu demektir:

```text
Kullanıcı önce Keycloak'a gider.
Keycloak login sonrası BFF'e direkt token vermez.
Önce authorization code verir.
BFF bu code ile Keycloak'a server-to-server istek atar.
Sonra token alır.
```

Akış:

```text
1. Browser → BFF
2. BFF → Keycloak login sayfasına redirect
3. Kullanıcı login olur
4. Keycloak → BFF'e authorization code gönderir
5. BFF → Keycloak token endpoint'ine code gönderir
6. Keycloak → BFF'e access token / id token verir
7. BFF tokenları session içinde saklar
```

Bu akış güvenlidir çünkü access token direkt browser’a verilmez.

---

# 9. Fotoğraf Açıklaması: BFF Araya Nasıl Giriyor?

## Sayfa 3’teki şema

Bu görselde BFF’in client ile Keycloak arasına girdiği anlatılıyor.

Normalde client Keycloak’a direkt gidip token alabilirdi.

Ama bizim yapıda:

```text
Client → BFF → Keycloak
```

BFF burada güvenli aracı rolündedir.

Client tarafı sadece şunu bilir:

```text
Ben localhost:9000'a istek atıyorum.
```

Ama arka planda BFF şunları yapar:

```text
Login kontrolü yapar.
Keycloak'a yönlendirir.
Tokenları alır.
Session'a koyar.
Gateway'e token ile istek atar.
```

---

# 10. Gateway ve BFF Arasındaki İlişki

BFF’in route ayarında şu yapı vardı:

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: gateway-server
              uri: lb://gateway-server
              predicates:
                - Path=/api/**
              filters:
                - TokenRelay
```

Burada şu deniyor:

```text
BFF'e /api/** ile gelen her isteği gateway-server servisine yönlendir.
```

`lb://gateway-server` demek:

```text
Gateway server'ı Eureka üzerinden bul.
```

`TokenRelay` demek:

```text
Session içinde bulunan OAuth2 access token'ı al.
Gateway'e Authorization: Bearer ... header'ı olarak ekle.
```

Yani frontend şunu yapar:

```text
GET http://localhost:9000/api/v1/products
```

BFF bunu şuna çevirir:

```text
GET gateway-server/api/v1/products
Authorization: Bearer access_token
```

---

# 11. Fotoğraf Açıklaması: Localhost 9000 Akışı

## Sayfa 4’teki görsel

Bu görselde kullanıcı `localhost:9000/api/v1/products` gibi bir adrese istek atıyor.

Eğer kullanıcı login değilse BFF onu Keycloak login ekranına yönlendirir.

Keycloak login ekranı şu adreste açılır:

```text
localhost:8087/realms/microservices-gygy5
```

Kullanıcı admin / password gibi bilgilerle giriş yaptıktan sonra Keycloak tekrar BFF’e döndürür.

Daha sonra kullanıcı asıl istediği endpoint’e devam eder.

Örnek:

```text
İlk istek:
http://localhost:9000/api/v1/products
```

Login yoksa:

```text
BFF → Keycloak login ekranına yönlendirir
```

Login başarılı olunca:

```text
Keycloak → BFF callback endpoint'ine döner
```

Sonra:

```text
BFF → Gateway → Product Service
```

ve ürün bilgisi ekrana gelir.

---

# 12. Session Nedir?

Session, sunucu tarafında tutulan kullanıcı oturum bilgisidir.

Basitçe:

```text
Kullanıcı login oldu mu?
Bu kullanıcıya ait token hangisi?
Bu kullanıcının oturum bilgileri ne?
```

gibi bilgileri tutar.

Browser tarafında genelde sadece bir cookie olur:

```text
JSESSIONID=abc123
```

Bu cookie access token değildir.

Bu sadece bir anahtar gibidir.

Server bu anahtara bakar ve der ki:

```text
Bu JSESSIONID hangi kullanıcıya ait?
Bu kullanıcının session'ında hangi token var?
```

Yani:

```text
Browser'da JSESSIONID var.
BFF server'da gerçek session bilgisi var.
```

---

# 13. Memory Session Ne Demek?

Hocanın anlattığı önemli nokta:

```text
Başta sessionları memory'de tutacağız.
Sonra Redis'e alacağız.
```

Memory session demek:

```text
Session bilgileri BFF uygulamasının RAM'inde tutulur.
```

Avantajı:

```text
Kurması kolaydır.
Ekstra Redis gibi bir servis gerekmez.
Geliştirme aşamasında hızlıdır.
```

Dezavantajı:

```text
BFF restart edilirse tüm kullanıcıların session'ı silinir.
Birden fazla BFF instance varsa kullanıcı hangi instance'a düşerse session orada aranır.
Scale etmek zordur.
```

Örneğin:

```text
BFF Server kapandı/açıldı → Session gider → Kullanıcı tekrar login olmak zorunda kalır.
```

Bu yüzden production ortamda daha iyi çözüm:

```text
Sessionları Redis'te tutmak.
```

Redis kullanılırsa:

```text
BFF restart olsa bile session Redis'te kalır.
Birden fazla BFF instance aynı session deposunu kullanabilir.
```

---

# 14. SSO Nedir?

SSO açılımı:

```text
Single Sign-On
```

Yani:

```text
Bir kere giriş yap, birden fazla uygulamada tekrar login olma.
```

Örnek:

```text
Google Mail'e login oldun.
Sonra Google Drive'a girdin.
Tekrar şifre sormaz.
```

Çünkü login merkezi bir sistem üzerinden yapılır.

Bizim örnekte merkezi sistem:

```text
Keycloak
```

---

# 15. Fotoğraf Açıklaması: SSO Şeması

## Sayfa 5’teki çizim

Görselde iki farklı uygulama gibi düşünebiliriz:

```text
Google Mail
Google Drive
```

İkisinin de kendi BFF’i olabilir.

Ama ikisi de aynı merkezi kimlik sağlayıcıya gider:

```text
Keycloak
```

Akış şöyle:

```text
1. Client Google Mail'e girer.
2. Mail BFF login kontrolü yapar.
3. Session yoksa Keycloak'a yönlendirir.
4. Kullanıcı Keycloak'ta login olur.
5. Keycloak kendi session'ını oluşturur.
6. Mail BFF kendi session'ını oluşturur.
7. Kullanıcı sonra Google Drive'a gider.
8. Drive BFF kendi session'ına bakar, yoktur.
9. Drive BFF Keycloak'a yönlendirir.
10. Keycloak der ki: Bu kullanıcı zaten bana login olmuş.
11. Kullanıcıya tekrar şifre sormadan Drive için login işlemini tamamlar.
```

Buradaki kritik nokta:

```text
SSO, farklı uygulamaların aynı Keycloak oturumunu kullanmasıyla sağlanır.
```

Yani Mail ve Drive aynı session’ı birebir paylaşmak zorunda değildir.

Asıl ortak nokta:

```text
Merkezi Identity Provider session'ı
```

Bizim örnekte bu:

```text
Keycloak session
```

---

# 16. SSO Session Paylaşımı Nasıl Anlaşılmalı?

SSO için şu ayrımı iyi bilmek gerekir:

```text
Uygulama session'ı
Identity Provider session'ı
```

Örneğin:

```text
Mail BFF session
Drive BFF session
Keycloak session
```

Mail BFF ile Drive BFF farklı session tutabilir.

Ama Keycloak merkezi session tuttuğu için kullanıcı tekrar login olmaz.

Yani SSO mantığı:

```text
Her uygulama kendi session'ını tutabilir.
Ama hepsi login için aynı Keycloak'a gider.
Keycloak'ta kullanıcı zaten login ise tekrar şifre sormaz.
```

---

# 17. Custom Login Yaparsam Ne Olur?

Derste sorulan sorulardan biri şuydu:

```text
Kendi custom login ekranımı yapmak istersem kullanıcı adı ve şifreyi Keycloak'a mı göndereceğim?
Keycloak benim user_db'ye mi erişecek?
Ayrı user_db tutmam gerekir mi?
```

Burada birkaç farklı yaklaşım var.

## 1. En güvenli ve standart yaklaşım

Kullanıcıyı Keycloak login sayfasına yönlendirirsin.

```text
Frontend/BFF → Keycloak Login Page
```

Bu durumda kullanıcı adı ve şifreyi senin uygulaman hiç görmez.

En doğru yaklaşım çoğu zaman budur.

## 2. Keycloak kendi kullanıcılarını tutabilir

Keycloak içinde kullanıcı oluşturabilirsin.

```text
Users → Add user
```

Bu durumda kullanıcı bilgileri Keycloak tarafında yönetilir.

## 3. Keycloak dış kullanıcı kaynağına bağlanabilir

Keycloak LDAP, Active Directory veya başka identity provider sistemlerine bağlanabilir.

## 4. Ayrı user-service ne işe yarar?

Mikroservislerde ayrıca `user-service` olabilir.

Ama bu servis genelde şunları tutar:

```text
Kullanıcının uygulama profili
Adres
Telefon
Rol detayları
Müşteri bilgileri
Tercihler
```

Ama authentication yani şifre doğrulama Keycloak’a bırakılır.

Özet:

```text
Kimlik doğrulama → Keycloak
Uygulama kullanıcı profili → user-service
```

---

# 18. Google ile Giriş / Facebook ile Giriş Keycloak ile Nasıl Olur?

Soru:

```text
Normalde Google ile giriş, Facebook ile giriş için kendi kodumuzda entegrasyon yapıyoruz.
Keycloak bunu kendi mi yapıyor?
```

Evet, Keycloak bunu yapabilir.

Keycloak içinde:

```text
Identity Providers
```

bölümünden Google, Facebook, GitHub gibi sağlayıcılar eklenebilir.

Bu durumda uygulama sadece Keycloak ile konuşur.

Akış:

```text
Uygulama → Keycloak
Keycloak → Google
Google → Keycloak
Keycloak → Uygulama
```

Yani bizim uygulama Google ile direkt uğraşmaz.

Bu tasarımın avantajı:

```text
Uygulama sadece Keycloak'ı bilir.
Google/Facebook/GitHub entegrasyonlarını Keycloak yönetir.
```

---

# 19. Logout Endpoint’i Neden Önemli?

Login kadar logout da önemlidir.

Çünkü kullanıcı çıkış yaptığında sadece BFF session’ını silmek yetmeyebilir.

Şu iki oturum vardır:

```text
1. BFF session
2. Keycloak session
```

Sadece BFF session silinirse:

```text
Kullanıcı tekrar /api isteği atar.
BFF session yok der.
Keycloak'a yönlendirir.
Ama Keycloak session hâlâ varsa kullanıcı tekrar şifre girmeden giriş yapar.
```

Bu yüzden logout sırasında Keycloak oturumu da kapatılmalıdır.

Buna:

```text
RP-Initiated Logout
```

denir.

---

# 20. Logout Kodunun Mantığı

Derste şu yapı vardı:

```java
private OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler(
        ClientRegistrationRepository clientRegistrationRepository) {
    var handler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
    handler.setPostLogoutRedirectUri("{baseUrl}");
    handler.setRedirectStrategy((request, response, url) -> {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader("Location", url);
    });
    return handler;
}
```

Bu kodun amacı:

```text
Logout sonrası Keycloak oturumunu da kapatmak.
```

Normalde logout sonrası Spring Security 302 redirect döndürebilir.

Ama frontend SPA ise fetch ile logout çağrısı yaptığında redirect takip edilirken CORS problemine takılabilir.

Bu yüzden hoca şunu yaptırmış:

```text
302 redirect dönme.
200 OK dön.
Location header içine Keycloak logout URL'ini koy.
Frontend bu Location değerini alıp window.location ile yönlendirsin.
```

Yani frontend tarafı şöyle davranabilir:

```text
POST /logout
Response: 200 OK
Location: Keycloak logout URL

window.location.href = response.headers.get("Location")
```

---

# 21. Silent Refresh Nedir?

Silent refresh şu demektir:

```text
Kullanıcı fark etmeden access token yenileme işlemi yapılması.
```

SPA uygulamalarda bazen iframe veya refresh token ile access token yenilenir.

Ama BFF mimarisinde token frontend’de olmadığı için silent refresh mantığı farklıdır.

BFF tarafında tokenlar server-side tutulduğu için frontend access token yenilemekle uğraşmaz.

Frontend açısından:

```text
Ben sadece cookie ile istek atarım.
Token var mı, süresi doldu mu, yenilenecek mi BFF halleder.
```

Bu yüzden BFF mimarisinde frontend’in klasik anlamda silent refresh yapmasına genelde gerek kalmaz.

---

# 22. Mobil Uygulamalarda Durum Nasıldır?

Derste mobil uygulama için şu soru sorulmuş:

```text
Mobil uygulamalarda PKCE Authorization Code Flow kullanılıyor.
Access token ve refresh token mobil uygulamaya gidiyor.
Bu yanlış mı?
```

Mobil uygulama ile browser tabanlı frontend aynı değildir.

Mobil uygulamalarda sık kullanılan güvenli yöntem:

```text
Authorization Code Flow + PKCE
```

PKCE şunu sağlar:

```text
Authorization code ele geçirilse bile token almak için code_verifier gerekir.
```

Mobil uygulamalarda token cihazda güvenli storage içinde saklanabilir.

Örneğin:

```text
iOS Keychain
Android Keystore
```

Bu yüzden mobil uygulamada token’ın mobil uygulamaya gitmesi her zaman hatalı değildir.

Ama web SPA tarafında token’ı browser’da tutmak daha risklidir.

Özet:

```text
Web SPA için BFF daha güvenli bir modeldir.
Mobil için Authorization Code + PKCE standart ve yaygın bir modeldir.
```

---

# 23. CSRF Nedir?

CSRF açılımı:

```text
Cross-Site Request Forgery
```

Türkçesi:

```text
Siteler arası istek sahteciliği
```

CSRF şu mantıkla çalışır:

Kullanıcı gerçek siteye login olmuştur:

```text
trendyol.com
```

Browser’da o siteye ait cookie vardır.

Sonra kullanıcı kötü niyetli başka bir siteye girer:

```text
freeiphone15.com
```

Bu kötü site arka planda şöyle bir form gönderebilir:

```html
<form action="https://trendyol.com/..." method="POST">
</form>
```

Browser cookie’leri otomatik gönderdiği için gerçek site bu isteği kullanıcı göndermiş gibi algılayabilir.

---

# 24. Fotoğraf Açıklaması: CSRF Şeması

## Sayfa 7’deki görsel

Görselde iki site var:

```text
trendyol.com
freeiphone15.com
```

Kullanıcı Trendyol’a login olduğu için browser’da Trendyol cookie’si var.

Kötü niyetli site, Trendyol’a form post ederse browser cookie’yi otomatik ekleyebilir.

Bu yüzden sunucu sadece cookie’ye güvenirse güvenlik açığı oluşur.

Bunu önlemek için CSRF token kullanılır.

---

# 25. Double Submit Cookie Nedir?

Sayfa 7’deki ikinci çizimde şu anlatılıyor:

```text
Double Submit Cookie
```

Mantık:

```text
Sunucu frontend'e XSRF-TOKEN adlı cookie verir.
Frontend bu cookie'yi okur.
Sonra her POST/PUT/DELETE isteğinde aynı token'ı header olarak gönderir.
```

Örnek:

```text
Cookie:
XSRF-TOKEN=abc123

Header:
X-XSRF-TOKEN=abc123
```

Sunucu kontrol eder:

```text
Cookie'deki token ile header'daki token aynı mı?
```

Kötü niyetli başka site cookie’yi otomatik gönderebilir ama cookie değerini okuyup header’a koyamaz.

Bu yüzden CSRF saldırısı engellenir.

---

# 26. BFF SecurityConfig İçindeki CSRF Mantığı

BFF içindeki şu kısım vardı:

```java
.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
```

Bunun anlamı:

```text
CSRF token cookie olarak üretilecek.
Frontend bu cookie'yi okuyabilecek.
Frontend istekte X-XSRF-TOKEN header olarak geri gönderecek.
```

`withHttpOnlyFalse()` önemli.

Çünkü cookie HttpOnly olursa JavaScript okuyamaz.

Burada amaç frontend’in XSRF token’ı okuyup header’a koyabilmesidir.

Ama bu access token değildir.

Access token yine frontend’e verilmez.

---

# 27. Access Token ile CSRF Token Karıştırılmamalı

Bu çok önemli:

```text
Access token ≠ CSRF token
```

Access token:

```text
Kullanıcının API'lere erişim yetkisini temsil eder.
BFF tarafında saklanır.
Frontend'e verilmez.
```

CSRF token:

```text
Cookie tabanlı isteklerde sahte POST/PUT/DELETE isteklerini engeller.
Frontend tarafından okunup header'a koyulabilir.
```

Yani CSRF token’ın frontend’de görünmesi problem değildir.

Ama access token’ın frontend’de görünmesi güvenlik problemidir.

---

# 28. Frontend Neden Eklendi?

Derste Angular frontend eklendi.

Amaç büyük bir frontend yapmak değildi.

Amaç:

```text
BFF ve Auth akışını test edebilmek.
```

Frontend’in görevi basitti:

```text
/api/v1/products endpoint'ine GET isteği at.
Gelen cevabı ekrana bas.
```

Akış:

```text
Angular Frontend → BFF /api/v1/products → Gateway → Product Service
```

Frontend direkt product-service’e gitmez.

Frontend direkt gateway’e de gitmez.

Frontend sadece BFF’i bilir.

---

# 29. Test Akışı Nasıl Olmalı?

Sistemi test etmek için servisler şu sırayla çalıştırılır:

```text
1. Config Server
2. Eureka Server
3. Keycloak
4. Gateway Server
5. Product Service
6. BFF Server
7. Frontend
```

Sonra tarayıcıdan frontend açılır.

Frontend ürünleri çekmek için BFF’e istek atar:

```text
GET http://localhost:9000/api/v1/products
```

Eğer kullanıcı login değilse:

```text
Keycloak login ekranına yönlendirilir.
```

Login sonrası:

```text
BFF session oluşur.
Token BFF tarafında tutulur.
Frontend sadece cookie taşır.
İstek Gateway üzerinden Product Service'e gider.
```

---

# 30. Kontrol Etmen Gereken Şeyler

Tarayıcı Developer Tools üzerinden şunlar kontrol edilir:

## Application / Cookies

Şunlar görülebilir:

```text
JSESSIONID
XSRF-TOKEN
```

Ama şunlar görünmemeli:

```text
access_token
refresh_token
id_token
```

Eğer tokenlar localStorage/sessionStorage içinde görünüyorsa BFF mantığı bozulmuş demektir.

## Network Tab

İstek şu adrese gitmeli:

```text
localhost:9000/api/v1/products
```

Frontend’in direkt şuralara gitmemesi gerekir:

```text
localhost:8889
localhost:8082
```

Çünkü frontend sadece BFF’i bilmelidir.

---

# 31. BFF ve Gateway Farkı

BFF ile Gateway aynı şey değildir.

## Gateway

Gateway’in görevi:

```text
Backend servislerine gelen istekleri yönlendirmek.
Routing yapmak.
Servisleri discovery ile bulmak.
Rate limit, circuit breaker, retry gibi işlemleri merkezi yapmak.
```

## BFF

BFF’in görevi:

```text
Frontend'e özel backend katmanı olmak.
Session yönetmek.
Tokenları frontend’den saklamak.
Frontend'in ihtiyaç duyduğu API akışını sadeleştirmek.
```

Bizdeki yapı:

```text
Frontend → BFF → Gateway → Microservices
```

---

# 32. AGENT.MD Neden Hazırlandı?

Derste ayrıca bir `AGENT.MD` dosyası hazırlandığı yazıyor.

Bu dosyanın amacı:

```text
Projede çalışan insan veya yapay zeka ajanlarının nasıl çalışacağını belirlemek.
```

Önemli kurallar:

```text
1. Tek seferde maksimum 10 dosya düzenle.
2. Eksik bilgi varsa uydurma.
3. Kodlamadan önce plan hazırla.
4. Hangi dosyalar değişecek belirt.
5. Yeni dependency varsa nedenini söyle.
6. Onay almadan implementasyona başlama.
```

Bu aslında proje düzenini korumak içindir.

Hoca burada şunu göstermek istemiş olabilir:

```text
Büyük projelerde direkt kod yazmak yerine önce plan yapmak gerekir.
```

---

# 33. Claude Init / Init Nedir?

Derste şöyle bir soru geçmiş:

```text
/init yapmak hatta bu komutla detayları vermek geliştirme kısmında kolaylık sağlar mı?
Claude init ile projemizin mevcut yapısını taratmış oluruz.
```

Buradaki `init` mantığı şudur:

```text
AI araca projeyi tanıtmak.
Proje yapısını analiz ettirmek.
Kuralları ve çalışma prensiplerini oluşturmak.
```

Yani `init` komutu genelde:

```text
Projeyi tara.
Hangi teknoloji kullanılıyor öğren.
Klasör yapısını çıkar.
Kodlama standartlarını anla.
Gerekirse AGENT.MD gibi yönerge dosyası oluştur.
```

işlerini kolaylaştırır.

Ama yine de AI’ın kafasına göre kod yazmaması gerekir.

Bu yüzden AGENT.MD içinde:

```text
Önce planla, sonra kodla.
```

kuralı vardır.

---

# 34. Her Implementasyonda Commit Atmak

Derste geçen best practice:

```text
Her implementasyonda bir commit at.
```

Bunun sebebi:

```text
Değişiklikleri küçük parçalara bölmek.
Hata olursa geri dönebilmek.
Hangi değişikliğin ne yaptığını takip etmek.
Code review'u kolaylaştırmak.
```

Örneğin BFF için commitler şöyle olabilir:

```text
feat: add bff-server module
feat: configure keycloak oauth2 login for bff
feat: add token relay route to gateway
feat: add csrf support for spa
feat: add logout endpoint behavior
feat: add angular frontend for auth flow test
```

---

# 35. Bu Derste Yapılanların Büyük Resmi

Tüm sistemin büyük resmi:

```text
Angular Frontend
      |
      | Cookie ile istek
      v
BFF Server
      |
      | TokenRelay ile Authorization header ekler
      v
Gateway Server
      |
      | Route eder
      v
Product Service / User Service / Order Service
```

Login tarafı:

```text
BFF Server
      |
      | OAuth2 Authorization Code Flow
      v
Keycloak
```

Session tarafı:

```text
Browser:
  JSESSIONID cookie

BFF:
  Session içinde OAuth2AuthorizedClient
  Access token
  Refresh token
```

CSRF tarafı:

```text
Browser:
  XSRF-TOKEN cookie

Frontend:
  X-XSRF-TOKEN header

BFF:
  CSRF doğrulaması
```

---

# 36. Sorular

## Soru 1: BFF neden kullanılır?

Cevap:

```text
Frontend ile backend arasında güvenli bir katman oluşturmak için kullanılır.
Access token frontend'e verilmez.
Tokenlar BFF tarafında session içinde tutulur.
Frontend sadece cookie ile çalışır.
```

## Soru 2: TokenRelay ne işe yarar?

Cevap:

```text
BFF session içinde bulunan access token'ı alır.
Gateway'e giden isteğe Authorization: Bearer header olarak ekler.
```

## Soru 3: Client authentication neden ON yaptık?

Cevap:

```text
BFF backend tarafında çalışan güvenilir bir uygulamadır.
Bu yüzden Keycloak'a client secret ile kendini doğrulayabilir.
Frontend olsaydı secret güvenli saklanamayacağı için client authentication açılmazdı.
```

## Soru 4: Session memory’de tutulursa ne olur?

Cevap:

```text
Session RAM'de tutulur.
BFF restart edilirse kullanıcıların oturumu silinir.
Birden fazla instance olduğunda problem çıkar.
Production için Redis gibi merkezi session store daha uygundur.
```

## Soru 5: SSO nasıl çalışır?

Cevap:

```text
Uygulamalar aynı merkezi Identity Provider'a gider.
Kullanıcı Keycloak'ta login olduysa diğer uygulamalarda tekrar şifre sormadan giriş yapılabilir.
```

## Soru 6: CSRF neden gerekir?

Cevap:

```text
Cookie tabanlı sistemlerde browser cookie'yi otomatik gönderir.
Kötü niyetli başka bir site kullanıcı adına istek gönderebilir.
CSRF token ile isteğin gerçekten bizim frontend'den gelip gelmediği kontrol edilir.
```

## Soru 7: Access token ile CSRF token farkı nedir?

Cevap:

```text
Access token API erişimi sağlar ve gizli kalmalıdır.
CSRF token sahte istekleri engellemek için kullanılır ve frontend tarafından header'a eklenebilir.
```

## Soru 8: Logout sırasında neden Keycloak session da kapatılır?

Cevap:

```text
Sadece BFF session silinirse Keycloak session aktif kalır.
Kullanıcı tekrar geldiğinde şifre sormadan login olabilir.
Tam logout için Keycloak oturumu da sonlandırılmalıdır.
```

## Soru 9: Mobil uygulamada token gitmesi yanlış mı?

Cevap:

```text
Web SPA için token browser'da tutulması risklidir.
Mobil uygulamada Authorization Code Flow + PKCE kullanılıyorsa token cihazdaki güvenli storage içinde tutulabilir.
Bu standart ve yaygın bir yaklaşımdır.
```

---

# 37. Kısa Ezber Özeti

```text
BFF = Frontend için özel backend.
Amaç = Token frontend'e sızmasın.
Login = Keycloak üzerinden Authorization Code Flow.
Token = BFF session içinde.
Frontend = Sadece cookie taşır.
Gateway = Servislere route eder.
TokenRelay = BFF'teki tokenı Gateway'e Authorization header olarak ekler.
Session = Başta memory, sonra Redis.
SSO = Tek login ile birden fazla uygulama.
CSRF = Cookie tabanlı sahte istekleri engeller.
Logout = Hem BFF session hem Keycloak session kapanmalı.
```

---

