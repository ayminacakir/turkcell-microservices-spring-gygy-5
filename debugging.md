# Debugging Kısa Tekrar Notları

---

## 1. Debugging Nedir?

**Debugging**, kodun neden beklediğimiz gibi çalışmadığını adım adım inceleme sürecidir.

Normalde kodu çalıştırınca sadece sonucu görürüz.  
Debug modunda ise kodun içine gireriz ve şunları izleriz:

- Hangi satır çalışıyor?
- Değişkenlerin değeri ne?
- `if` bloğuna giriyor mu?
- Method hangi sonucu dönüyor?
- Hata hangi satırda oluşuyor?
- Repository/service/controller akışı doğru mu?

Kısaca:

> Debugging, kodun çalışma anını izleyerek hatanın nerede oluştuğunu bulmaktır.

---

## 2. Debugging Neden Yapılır?

Debugging şu durumlarda kullanılır:

- Kod hata veriyor ama nedenini anlamıyorsan
- Beklediğin sonuç gelmiyorsa
- `if` bloğuna girip girmediğini kontrol etmek istiyorsan
- API isteği controller’a geliyor mu görmek istiyorsan
- Service içindeki değerler doğru mu görmek istiyorsan
- Repository sonucu null mı geliyor anlamak istiyorsan
- Bir exception’ın hangi satırda oluştuğunu bulmak istiyorsan

---

## 3. Breakpoint Nedir?

**Breakpoint**, kodun durmasını istediğin satırdır.

Debug modda uygulama breakpoint’e gelince durur.  
Sen de o anda değişkenleri, object değerlerini ve akışı incelersin.

Örnek:

```java
public Customer createCustomer(Customer customer) {

    boolean exists = customerRepository.existsByIdentityNumber(customer.getIdentityNumber());

    if (exists) {
        throw new RuntimeException("Bu kimlik numarası ile kayıtlı müşteri zaten var.");
    }

    customer.setStatus("PENDING");

    return customerRepository.save(customer);
}
```

Burada breakpoint koyulabilecek iyi yerler:

```java
boolean exists = customerRepository.existsByIdentityNumber(customer.getIdentityNumber());
```

Çünkü burada şunu anlarsın:

> Bu TCKN gerçekten var mı görünüyor?

Şuraya da koyabilirsin:

```java
if (exists) {
```

Çünkü burada şunu anlarsın:

> Kod hata bloğuna girecek mi?

---

## 4. Breakpoint Nereye Konur?

Breakpoint rastgele konmaz.  
Şu noktalara koymak mantıklıdır:

### 1. Controller girişine

API isteği geliyor mu görmek için.

```java
@PostMapping
public Customer create(@RequestBody Customer customer) {
    return customerService.createCustomer(customer);
}
```

Burada breakpoint koyarsan:

- Request geldi mi?
- Body doğru maplendi mi?
- `customer` null mı?
- Alanlar dolu mu?

görebilirsin.

---

### 2. Service methodunun başına

İş mantığı doğru çalışıyor mu görmek için.

```java
public Customer createCustomer(Customer customer) {
```

Burada breakpoint koyarsan:

- Controller’dan service’e veri geldi mi?
- Gelen değerler doğru mu?

görebilirsin.

---

### 3. Karar noktalarına

`if`, `switch`, validation veya business rule satırlarına koyulur.

```java
if (exists) {
    throw new RuntimeException("Müşteri zaten var.");
}
```

Burada breakpoint koyarsan:

- `exists` true mu false mu?
- Kod hangi yola girecek?

görebilirsin.

---

### 4. Repository çağrısından hemen sonrasına

Veritabanından ne döndüğünü görmek için.

```java
Optional<Customer> customer = customerRepository.findById(id);
```

Burada breakpoint koyarsan:

- Customer bulundu mu?
- Optional boş mu?
- Gelen entity doğru mu?

görebilirsin.

---

### 5. Hata aldığın satırdan önce

Hata örneği:

```text
NullPointerException
```

Bu hatayı aldığın satıra değil, genelde **birkaç satır öncesine** breakpoint koyarsın.  
Çünkü hatayı oluşturan değer orada hazırlanıyordur.

---

## 5. Debug Butonları Ne İşe Yarar?

Debug yaparken en çok kullanılan butonlar şunlardır.

---

### Continue / Resume

Programı bir sonraki breakpoint’e kadar çalıştırır.

```text
Breakpointte durduysan ve devam etmek istiyorsan kullanılır.
```

---

### Step Over

Bulunduğun satırı çalıştırır ama methodun içine girmez.

Örnek:

```java
customerService.createCustomer(customer);
```

Step Over yaparsan:

- Bu method çalışır.
- Ama methodun içine girmez.
- Sonraki satıra geçer.

Kullanım:

> Methodun iç detayına girmek istemiyorsan Step Over kullanılır.

---

### Step Into

Methodun içine girer.

Örnek:

```java
customerService.createCustomer(customer);
```

Step Into yaparsan `createCustomer` methodunun içine girersin.

Kullanım:

> Hatanın methodun içinde olduğunu düşünüyorsan Step Into kullanılır.

---

### Step Out

İçinde bulunduğun methoddan çıkar.

Kullanım:

> Yanlışlıkla methodun içine girdin veya artık içini incelemek istemiyorsun. Step Out ile dışarı çıkarsın.

---

### Restart

Debug oturumunu baştan başlatır.

---

### Stop

Debug oturumunu durdurur.

---

## 6. Variables Paneli Nedir?

Debug sırasında değişkenlerin anlık değerlerini gösterir.

Örnek:

```java
Customer customer = new Customer();
customer.setFirstName("Aymina");
```

Variables panelinde şunları görebilirsin:

```text
customer.firstName = "Aymina"
customer.lastName = "Çakır"
customer.status = null
```

Bu panel sayesinde kodun o anda hangi veriyle çalıştığını anlarsın.

---

## 7. Evaluate Expression Nedir?

**Evaluate Expression**, debug sırasında bir ifade çalıştırıp sonucunu görmektir.

Yani kodu değiştirmeden o anda küçük kontroller yapabilirsin.

Örnek debug sırasında elinde şu nesne var:

```java
Customer customer
```

Evaluate kısmına şunu yazabilirsin:

```java
customer.getIdentityNumber()
```

Sonuç:

```text
"12345678910"
```

Başka örnekler:

```java
customer.getFirstName().toUpperCase()
```

```java
customer.getStatus() == null
```

```java
customerRepository.existsByIdentityNumber(customer.getIdentityNumber())
```

Dikkat:

> Evaluate bazen gerçekten method çalıştırabilir. Özellikle save, delete, update gibi veri değiştiren methodları evaluate ile çalıştırmak tehlikelidir.

Güvenli evaluate örnekleri:

```java
customer.getFirstName()
customer.getIdentityNumber()
exists
customer == null
customer.getStatus()
```

Riskli evaluate örnekleri:

```java
customerRepository.save(customer)
customerRepository.delete(customer)
paymentService.chargePayment(request)
```

---

## 8. Watch Nedir?

**Watch**, sürekli takip etmek istediğin ifadeleri eklediğin alandır.

Örneğin debug boyunca şu değeri izlemek istiyorsun:

```java
customer.getStatus()
```

Bunu Watch’a eklersen her adımda değeri değişiyor mu görürsün.

Watch özellikle şunlarda faydalıdır:

- Sayaçlar
- Status değişimleri
- Null kontrolü
- Hesaplanan toplam tutarlar
- Request içindeki alanlar

---

## 9. Call Stack Nedir?

**Call Stack**, kodun hangi methodlardan geçerek bulunduğun satıra geldiğini gösterir.

Örnek akış:

```text
CustomersController.create()
CustomerService.createCustomer()
CustomerRepository.save()
```

Call Stack sayesinde şunu anlarsın:

> Bu methodu kim çağırdı?

Mülakatta şöyle anlatabilirsin:

> Call stack, hata oluşan satıra gelene kadar hangi methodların sırayla çağrıldığını gösterir.

---

## 10. Debugging Çeşitleri

### 1. Breakpoint Debugging

IDE üzerinden breakpoint koyarak adım adım inceleme.

En yaygın debugging türüdür.

---

### 2. Logging ile Debugging

Kodun belli yerlerine log koyarak akışı izleme.

```java
System.out.println("Customer id: " + customer.getId());
```

Daha profesyonel kullanım:

```java
log.info("Customer created with identityNumber: {}", customer.getIdentityNumber());
```

Loglama özellikle production ortamında önemlidir.  
Çünkü production’da genelde breakpoint ile durduramazsın.

---

### 3. Exception Stack Trace İnceleme

Hata logunda şu kısma bakılır:

```text
Caused by:
```

veya ilk kendi yazdığın class satırına bakılır.

Örnek:

```text
java.lang.NullPointerException
    at com.turkcell.customer_service.service.CustomerService.createCustomer(CustomerService.java:25)
```

Bu şu demektir:

> Hata CustomerService class’ında 25. satırda oluşmuş.

---

### 4. Unit Test ile Debugging

Bir methodun neden hata verdiğini anlamak için unit test yazıp debug edebilirsin.

Örnek:

```java
@Test
void createCustomer_ShouldThrowException_WhenIdentityNumberAlreadyExists() {
    // test
}
```

Bu testi debug modda çalıştırıp sadece ilgili methodu inceleyebilirsin.

---

### 5. Remote Debugging

Uygulama başka bir ortamda çalışırken IDE ile bağlanıp debug etmektir.

Örneğin Docker içindeki veya sunucudaki uygulamayı debug etmek.

Bu ileri seviye bir konudur ama mülakatta duyabilirsin.

---

## 11. Spring Boot’ta Debug Akışı

Bir API hatasında genelde şu sırayla ilerlenir:

```text
Controller
↓
Service
↓
Business Rule
↓
Repository
↓
Database sonucu
↓
Response
```

Örnek endpoint:

```java
@PostMapping
public Customer create(@RequestBody Customer customer) {
    return customerService.createCustomer(customer);
}
```

Debug sırası:

1. Controller’a breakpoint koy.
2. Request geliyor mu kontrol et.
3. Body doğru maplenmiş mi bak.
4. Service methoduna Step Into ile gir.
5. Business rule değerlerini kontrol et.
6. Repository sonucu doğru mu bak.
7. Return edilen response doğru mu kontrol et.

---

## 12. Kod Üzerinden Debugging Örneği

Aşağıdaki kodda aynı TCKN varsa hata fırlatılıyor.

```java
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer createCustomer(Customer customer) {

        boolean exists = customerRepository.existsByIdentityNumber(customer.getIdentityNumber());

        if (exists) {
            throw new RuntimeException("Bu kimlik numarası ile kayıtlı müşteri zaten var.");
        }

        customer.setStatus("PENDING");

        return customerRepository.save(customer);
    }
}
```

### Nereye breakpoint koyarım?

```java
public Customer createCustomer(Customer customer) {
```

Burada gelen customer dolu mu bakarım.

```java
boolean exists = customerRepository.existsByIdentityNumber(customer.getIdentityNumber());
```

Burada identityNumber doğru mu ve repository sonucu ne bakarım.

```java
if (exists) {
```

Burada kod hata bloğuna girecek mi bakarım.

```java
return customerRepository.save(customer);
```

Burada save öncesi customer’ın son hali doğru mu bakarım.

---

## 13. Debug Sırasında Kontrol Edeceğim Değerler

Bu örnekte şunlara bakılır:

```java
customer
customer.getIdentityNumber()
exists
customer.getStatus()
```

Evaluate ile şunları çalıştırabilirsin:

```java
customer.getIdentityNumber()
```

```java
exists
```

```java
customer.getStatus()
```

```java
customer == null
```

---

## 14. NullPointerException Debugging Örneği

Hata:

```text
Cannot invoke "String.length()" because "name" is null
```

Kod:

```java
public int getNameLength(Customer customer) {
    String name = customer.getFirstName();
    return name.length();
}
```

Sorun:

```java
name
```

null olabilir.

Debug yaparken breakpoint koy:

```java
String name = customer.getFirstName();
```

Sonra Variables panelinde bak:

```text
customer.firstName = null
```

Çözüm:

```java
public int getNameLength(Customer customer) {
    if (customer.getFirstName() == null) {
        return 0;
    }

    return customer.getFirstName().length();
}
```

Mülakat cevabı:

> NullPointerException aldığımda önce stack trace’te hatanın hangi satırda olduğunu bulurum. Sonra o satırdan önce breakpoint koyup null olabilecek değişkenleri kontrol ederim.

---

## 15. Liste / Döngü Debugging Örneği

Kod:

```java
public int calculateTotal(List<Integer> prices) {
    int total = 0;

    for (Integer price : prices) {
        total += price;
    }

    return total;
}
```

Breakpoint:

```java
total += price;
```

Her döngüde şunları izlersin:

```text
price
total
```

Watch’a şunları ekleyebilirsin:

```java
price
total
```

Bu şekilde toplamın nasıl değiştiğini görürsün.

---

## 16. Conditional Breakpoint Nedir?

**Conditional breakpoint**, sadece belirli şart sağlanınca duran breakpoint’tir.

Örnek:

```java
for (Customer customer : customers) {
    System.out.println(customer.getIdentityNumber());
}
```

Sadece belirli TCKN’ye gelince durmak istiyorsan koşul yazarsın:

```java
customer.getIdentityNumber().equals("12345678910")
```

Bu özellikle büyük listelerde çok işe yarar.

Mülakatta şöyle anlatabilirsin:

> Conditional breakpoint, döngü içinde her elemanda durmak yerine sadece belirli koşul sağlanınca durmamı sağlar.

---

## 17. Exception Breakpoint Nedir?

**Exception breakpoint**, belirli bir exception fırlatıldığında otomatik durmayı sağlar.

Örneğin uygulamada `NullPointerException` nerede oluşuyor bulmak istiyorsan exception breakpoint ekleyebilirsin.

Bu durumda kod o exception fırlatıldığı anda durur.

---

## 18. Debugging ve Logging Farkı

| Debugging | Logging |
|---|---|
| Geliştirme sırasında adım adım inceleme | Çalışma sırasında kayıt bırakma |
| Breakpoint ile kod durur | Kod durmaz |
| Değişkenleri anlık görürsün | Loga yazdığın kadarını görürsün |
| Lokal geliştirme için çok uygundur | Production için daha uygundur |

Kısaca:

> Lokal ortamda breakpoint ile debug yapılır. Production ortamında loglar üzerinden analiz yapılır.

---

## 19. Debugging Yaparken Sık Yapılan Hatalar

- Rastgele her yere breakpoint koymak
- Hatanın olduğu satıra değil, çok sonrasına breakpoint koymak
- Variables paneline bakmadan sürekli Step Over yapmak
- Step Into ile gereksiz library kodlarının içine girmek
- Evaluate ile veri değiştiren method çalıştırmak
- Stack trace okumadan tahmin yürütmek
- Controller’a request geliyor mu kontrol etmeden service’e bakmak
- Port / DB / config hatasını kod hatası sanmak

---

## 20. Mülakatta Debugging Nasıl Anlatılır?

Şöyle anlatabilirsin:

> Debugging, kodun çalışma anını adım adım inceleyerek hatanın kaynağını bulma sürecidir. Önce hata mesajını ve stack trace’i okurum. Sonra hatanın oluştuğu akışı belirlerim. Controller’dan başlayıp service, business rule ve repository katmanlarını kontrol ederim. Breakpoint koyarak değişkenlerin değerlerine bakarım. Gerekirse evaluate expression ve watch kullanırım. Eğer hata production’da ise breakpoint yerine loglar üzerinden analiz yaparım.

---

## 21. Mülakat Soruları ve Kısa Cevaplar

### Debugging nedir?

Kodun çalışma anını inceleyerek hatanın nerede ve neden oluştuğunu bulma sürecidir.

### Breakpoint nedir?

Kodun debug modda durmasını istediğimiz satırdır.

### Step Over nedir?

Bulunduğumuz satırı çalıştırır ama methodun içine girmez.

### Step Into nedir?

Çağrılan methodun içine girer.

### Step Out nedir?

Bulunduğumuz methoddan çıkar.

### Evaluate Expression nedir?

Debug sırasında bir ifadeyi çalıştırıp sonucunu görmemizi sağlar.

### Watch nedir?

Takip etmek istediğimiz değişken veya ifadeleri sürekli izlememizi sağlar.

### Call Stack nedir?

Kodun bulunduğu satıra hangi method çağrılarıyla geldiğini gösterir.

### Conditional Breakpoint nedir?

Sadece belirli bir koşul sağlandığında duran breakpoint’tir.

### Exception Breakpoint nedir?

Belirli bir exception fırlatıldığında debugger’ın otomatik durmasını sağlar.

### Debugging ile logging farkı nedir?

Debugging kodu durdurup adım adım incelemektir. Logging ise kod çalışırken olayları kaydetmektir.

---

## 22. Debugging Kontrol Listesi

Bir hata aldığında şu sırayı takip edebilirsin:

```text
1. Hata mesajını oku.
2. Stack trace içinde kendi class’ını bul.
3. Hatanın olduğu methodu aç.
4. Hata satırından birkaç satır önce breakpoint koy.
5. Debug modda çalıştır.
6. Variables panelinden değerleri kontrol et.
7. Gerekirse Step Into ile method içine gir.
8. Evaluate ile basit ifadeleri kontrol et.
9. Sorun null mı, yanlış değer mi, yanlış branch mi bul.
10. Düzeltip tekrar çalıştır.
```

---

## 23. Kısa Özet

```text
Debugging = Kodun çalışma anını adım adım incelemek.
Breakpoint = Kodun duracağı satır.
Step Over = Satırı çalıştır, method içine girme.
Step Into = Methodun içine gir.
Step Out = Methoddan çık.
Variables = O andaki değişken değerleri.
Evaluate = Debug sırasında ifade çalıştırıp sonucuna bakma.
Watch = Sürekli takip edilen ifadeler.
Call Stack = Method çağrı zinciri.
Conditional Breakpoint = Şart sağlanınca duran breakpoint.
Exception Breakpoint = Exception fırlatılınca duran breakpoint.
Logging = Kod durmadan kayıt bırakma.
```

---

## 24. Akılda Kalacak Cümle

> Debugging yaparken amaç tahmin etmek değil, kodun o anda hangi veriyle ve hangi akışla çalıştığını görmektir.
