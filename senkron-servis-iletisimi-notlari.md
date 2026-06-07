# Product Service ile User Service Arasında Senkron İletişim

Bu not, derste anlatılan **mikroservisler arası senkron iletişim** konusunu tekrar etmek için hazırlanmıştır. Amaç, Product Service ile User Service arasında basit bir örnek üzerinden bir servisin diğer servise nasıl istek attığını anlamaktır.

---

## 1. Konunun Ana Fikri

Mikroservis mimarisinde her servis kendi sorumluluğuna sahiptir.

Örneğin:

- `product-service` ürünlerle ilgili işlemleri yapar.
- `user-service` kullanıcılarla ilgili işlemleri yapar.

Bazı durumlarda bir servis, başka bir servisteki bilgiye ihtiyaç duyabilir. Bu durumda servisler birbirleriyle iletişim kurar.

Bu iletişim iki şekilde olabilir:

1. **Senkron iletişim**
2. **Asenkron iletişim**

Bu derste anlatılan konu **senkron iletişimdir**.

---

## 2. Senkron İletişim Nedir?

Senkron iletişimde bir servis, başka bir servise istek atar ve cevap gelene kadar bekler.

Bunu Postman örneğiyle düşünebiliriz:

> Ben Postman üzerinden bir endpoint'e istek atıyorum.  
> Karşılığında hemen bir cevap alıyorum.  
> Aynı mantığı Java kodu içinden de yapabiliyoruz.

Yani Java tarafında bir servis, başka bir servisin endpoint'ine istek atabilir.

---

## 3. Postman ile Java Arasındaki Benzerlik

Normalde Postman'da şöyle bir istek atabiliriz:

```text
GET http://localhost:8081/test
```

Bu isteğe karşılık şöyle bir cevap dönebilir:

```json
{
  "message": "Product Service Test Başarılı"
}
```

Java kodunda yaptığımız şey de aslında buna benzer:

```text
Postman yerine Java kodu istek atar.
```

Yani servislerden biri, diğer servisin endpoint'ine client gibi davranarak istek gönderir.

---

## 4. Client Rolü Ne Demek?

Senkron iletişimde bir servis bazen **client** rolüne geçer.

Client demek, istek atan taraf demektir.

Örneğin:

```text
User Service  --->  Product Service
```

Burada:

- `user-service` istek atan taraftır.
- `product-service` cevap dönen taraftır.

Bu durumda `user-service`, `product-service` için client gibi davranır.

---

## 5. Örnek Senaryo

Derste örnek olarak `product-service` içinde basit bir test endpoint'i yazıldı.

Amaç şuydu:

> Product Service çalışıyor mu?  
> Başka bir servis Product Service'e istek attığında cevap alabiliyor mu?

Bunun için önce Product Service tarafında bir response sınıfı oluşturuldu.

---

## 6. Product Service İçinde TestClass Oluşturma

İlk olarak `product-service` içinde basit bir record yazıldı.

```java
package com.turkcell.product_service.entity;

public record TestClass(String message) {}
```

### Bu Kod Ne İşe Yarar?

Bu sınıf, endpoint'ten dönecek cevabın şeklini belirler.

Burada sadece bir tane alan vardır:

```java
String message
```

Yani bu class şu tarz bir JSON döndürmek için kullanılır:

```json
{
  "message": "Product Service Test Başarılı"
}
```

---

## 7. `record` Ne Demek?

Java'da `record`, kısa yoldan veri taşıyan sınıf yazmamızı sağlar.

Normalde klasik bir class yazsaydık şunları yazmamız gerekebilirdi:

- field
- constructor
- getter
- equals
- hashCode
- toString

Ama `record` bunları bizim için otomatik oluşturur.

Yani şu kod:

```java
public record TestClass(String message) {}
```

Aslında basitçe şu anlama gelir:

```text
Ben içinde message alanı olan basit bir veri taşıma sınıfıyım.
```

---

## 8. Product Controller İçinde Test Endpoint'i Yazma

Daha sonra `product-service` controller içinde bir `GET` metodu yazıldı.

```java
@GetMapping("/test")
public TestClass test2() {
    return new TestClass("Product Service Test Başarılı");
}
```

### Bu Kod Ne Yapar?

Bu endpoint'e istek geldiğinde Product Service şu cevabı döner:

```json
{
  "message": "Product Service Test Başarılı"
}
```

Yani bu endpoint, Product Service'in çalıştığını test etmek için kullanılır.

---

## 9. Endpoint'in Tam Mantığı

Eğer Product Service şu portta çalışıyorsa:

```text
http://localhost:8081
```

O zaman endpoint şöyle olur:

```text
GET http://localhost:8081/test
```

Bu endpoint çağrıldığında şu akış gerçekleşir:

```text
1. İstek /test endpoint'ine gelir.
2. Controller içindeki test2() metodu çalışır.
3. test2() metodu yeni bir TestClass nesnesi oluşturur.
4. Bu nesnenin içine mesaj yazılır.
5. Spring Boot bu nesneyi JSON formatına çevirir.
6. JSON cevap olarak geri döner.
```

---

## 10. Spring Boot JSON'a Nasıl Çeviriyor?

Controller içinde biz Java nesnesi döndürüyoruz:

```java
return new TestClass("Product Service Test Başarılı");
```

Ama kullanıcıya veya diğer servise JSON gider:

```json
{
  "message": "Product Service Test Başarılı"
}
```

Bunu Spring Boot otomatik yapar.

Yani Java nesnesi response olarak döndüğünde Spring, bunu JSON'a serialize eder.

---

## 11. User Service Burada Ne Yapacak?

Product Service tarafında `/test` endpoint'i hazırlandıktan sonra, User Service bu endpoint'e istek atabilir.

Mantık şu şekildedir:

```text
User Service içinden Product Service'in /test endpoint'i çağrılır.
```

Akış:

```text
User Service  --->  Product Service /test
User Service  <---  Product Service Test Başarılı
```

Burada User Service, Postman gibi davranır. Ancak isteği manuel değil, Java kodu ile atar.

---

## 12. Senkron İletişim Akışı

Aşağıdaki şema bu yapıyı özetler:

```text
Kullanıcı veya Postman
        |
        v
User Service Endpoint'i
        |
        | Java kodu ile HTTP isteği atar
        v
Product Service /test Endpoint'i
        |
        | JSON cevap döner
        v
User Service cevabı alır
        |
        v
Kullanıcıya sonuç döner
```

---

## 13. Servisler Arası Senkron İletişimin Mantığı

Bu yapıda önemli olan şudur:

```text
Bir servis, başka bir servisin endpoint'ini çağırır.
```

Tıpkı tarayıcıdan veya Postman'dan istek atmak gibi, Java kodu da HTTP isteği atabilir.

Bu yüzden senkron iletişimde servisler HTTP üzerinden birbirleriyle konuşabilir.

---

## 14. Basit Örnek Kod Akışı

Product Service tarafı:

```java
@GetMapping("/test")
public TestClass test2() {
    return new TestClass("Product Service Test Başarılı");
}
```

User Service tarafı ise ilerleyen aşamada buna benzer şekilde Product Service'i çağırabilir:

```java
GET http://localhost:8081/test
```

Buradaki amaç, User Service'in Product Service'ten cevap alabilmesini sağlamaktır.

---

## 15. Neden Bu Örnek Yapıldı?

Bu örnek, mikroservisler arası iletişimin temelini anlamak için yapıldı.

Çünkü gerçek projelerde servisler birbirinden veri almak zorunda kalabilir.

Örneğin:

```text
Order Service, ürün bilgisi için Product Service'e istek atabilir.
User Service, sipariş geçmişi için Order Service'e istek atabilir.
Product Service, kullanıcı bilgisi için User Service'e istek atabilir.
```

Bu örnekte sadece test amaçlı basit bir mesaj döndürülüyor.

Ama mantık gerçek projelerde de aynıdır.

---

## 16. Senkron İletişimin Avantajı

Senkron iletişim basit ve anlaşılırdır.

Bir servis istek atar, diğer servis cevap verir.

Avantajları:

- Mantığı kolaydır.
- Postman mantığına benzer.
- Hemen cevap alınır.
- Test etmesi kolaydır.

---

## 17. Senkron İletişimin Dezavantajı

Senkron iletişimde istek atan servis, cevap gelene kadar bekler.

Bu yüzden çağrılan servis çalışmıyorsa sorun oluşabilir.

Örneğin:

```text
User Service, Product Service'e istek atıyor.
Ama Product Service kapalı.
```

Bu durumda User Service cevap alamaz ve hata oluşabilir.

Bu yüzden mikroservislerde şu konular önemlidir:

- timeout
- retry
- fallback
- circuit breaker
- error handling

---

## 18. Bu Derste Bilmem Gereken En Önemli Nokta

Bu konunun özü şudur:

> Senkron iletişimde bir servis, başka bir servisin endpoint'ine Java kodu üzerinden HTTP isteği atar ve cevap bekler.

Postman'da elle yaptığımız isteği, servis içinde kodla yapmış oluruz.

---

## 19. Kısa Tekrar

```text
Senkron iletişim = istek at, cevap bekle.
```

```text
Client = istek atan taraf.
```

```text
Server = isteği karşılayan ve cevap dönen taraf.
```

```text
Product Service içindeki /test endpoint'i, dışarıdan çağrıldığında mesaj döner.
```

```text
User Service, Product Service'e istek atarsa client rolüne geçer.
```

---

## 20. Dersteki Kodların Toplu Hali

### TestClass

```java
package com.turkcell.product_service.entity;

public record TestClass(String message) {}
```

### Product Controller Endpoint'i

```java
@GetMapping("/test")
public TestClass test2() {
    return new TestClass("Product Service Test Başarılı");
}
```

### Beklenen JSON Cevap

```json
{
  "message": "Product Service Test Başarılı"
}
```

---

## 21. Akılda Kalıcı Özet

Bu yapıyı şöyle düşünebiliriz:

```text
Postman ile endpoint çağırmak = manuel istek
Java kodu ile endpoint çağırmak = servisler arası istek
```

Yani:

```text
Ben Postman'dan nasıl Product Service'e istek atabiliyorsam,
User Service de Java kodu ile Product Service'e istek atabilir.
```

Bu yüzden senkron iletişimde bir mikroservis, başka bir mikroservisin client'ı gibi davranır.

