# Unit Test Tekrar Notları

**JUnit 5 + Mockito + Arrange / Act / Assert** mantığı.

---

## 1. Unit Test Nedir?

**Unit test**, kodun küçük bir parçasını tek başına test etmektir.

Genelde şunlar test edilir:

- Service methodları
- Mapper methodları
- Business rule methodları
- Utility/helper methodları
- Validation kontrolleri

Amaç:

> Method beklenen durumda doğru sonucu veriyor mu?

Unit testte gerçek veritabanına, gerçek API’ye veya Kafka’ya bağlanmayız.  
Bunların yerine **mock** kullanırız.

---

## 2. JUnit 5 Nedir?

**JUnit 5**, Java’da test yazmak için kullanılan test framework’üdür.

Bir methodun test olduğunu belirtmek için:

```java
@Test
void testMethodName() {
    // test kodu
}
```

Test class’ları genelde şu klasöre yazılır:

```text
src/test/java
```

Ana kod ile test kodunun package yapısı benzer tutulur.

Örnek:

```text
src/main/java/com/turkcell/customer_service/service/CustomerService.java
src/test/java/com/turkcell/customer_service/service/CustomerServiceTest.java
```

---

## 3. Arrange / Act / Assert

Unit testin temel yazım düzenidir.

```text
Arrange → Test verisini hazırla
Act     → Test edilecek methodu çalıştır
Assert  → Sonucu kontrol et
```

Örnek:

```java
@Test
void createCustomer_ShouldSaveCustomer_WhenIdentityNumberIsUnique() {
    // Arrange
    Customer customer = new Customer();
    customer.setFirstName("Aymina");
    customer.setIdentityNumber("12345678910");

    // Act
    Customer result = customerService.createCustomer(customer);

    // Assert
    assertEquals("Aymina", result.getFirstName());
}
```

Not: Bazen yanlışlıkla “Ack” denebilir ama doğru terim **Act**’tir.

---

## 4. Assert Nedir?

**Assert**, test sonucunu kontrol eder.

En çok kullanılanlar:

```java
assertEquals(expected, actual);
assertNotNull(value);
assertTrue(condition);
assertFalse(condition);
assertThrows(Exception.class, () -> methodCall());
```

Örnek:

```java
assertEquals("PENDING", result.getStatus());
```

Bu şu demektir:

> Beklenen değer PENDING, gerçek değer de PENDING mi?

---

## 5. Mock Nedir?

**Mock**, gerçek nesne yerine kullanılan sahte nesnedir.

Unit testte genelde **Dependency Injection ile class içine aldığımız bağımlılıklar** mocklanır.

Yani:

```text
Test edilen class → gerçek
Dış bağımlılıklar → mock
```

Genelde mocklananlar:

- Repository
- Başka bir service
- External API client
- Kafka producer
- Mail sender
- Payment provider client
- File storage client

Örnek:

```java
@Mock
private CustomerRepository customerRepository;

@InjectMocks
private CustomerService customerService;
```

Burada:

```text
CustomerService gerçek test edilen class
CustomerRepository mocklanan dependency
```

---

## 6. @Mock ve @InjectMocks

### @Mock

Sahte bağımlılık oluşturur.

```java
@Mock
private CustomerRepository customerRepository;
```

### @InjectMocks

Test edilen class’ı oluşturur ve içine mock bağımlılıkları koyar.

```java
@InjectMocks
private CustomerService customerService;
```

Mockito annotation’larının çalışması için test class’ına şu eklenir:

```java
@ExtendWith(MockitoExtension.class)
```

---

## 7. when / thenReturn Nedir?

Mock nesnenin nasıl davranacağını belirler.

```java
when(customerRepository.existsByIdentityNumber("12345678910"))
        .thenReturn(false);
```

Anlamı:

> Repository’ye bu TCKN var mı diye sorulursa false dön.

Başka örnek:

```java
when(customerRepository.save(customer))
        .thenReturn(customer);
```

---

## 8. verify Nedir?

Bir method gerçekten çağrıldı mı diye kontrol eder.

```java
verify(customerRepository).save(customer);
```

Anlamı:

> save methodu çağrıldı mı?

Hiç çağrılmamasını bekliyorsak:

```java
verify(customerRepository, never()).save(customer);
```

Anlamı:

> save methodu hiç çağrılmamalı.

---

## 9. Coverage Nedir?

**Coverage**, testlerin kodun ne kadarını çalıştırdığını gösterir.

Örnek:

```text
Line coverage: %80
```

Ama coverage tek başına kalite demek değildir.

İyi test şunları kontrol eder:

- Başarılı senaryo
- Hatalı senaryo
- Beklenen exception
- Sınır durumlar
- Gerekli method çağrıları

---

## 10. TDD Nedir?

**TDD = Test Driven Development**

Yani:

> Önce test yazılır, sonra kod yazılır.

Döngüsü:

```text
Red      → Önce fail olan test yaz
Green    → Testi geçirecek kodu yaz
Refactor → Kodu temizle
```

Amaç, kod yazmadan önce beklenen davranışı netleştirmektir.

---

# Örnek: CustomerService Unit Test

Senaryo:

1. TCKN daha önce yoksa müşteri kaydedilsin.
2. TCKN zaten varsa hata fırlatılsın ve kayıt yapılmasın.

---

## CustomerService

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
        customer.setCreatedAt(LocalDateTime.now());

        return customerRepository.save(customer);
    }
}
```

Bu service içinde test edeceğimiz kurallar:

- Aynı TCKN varsa hata fırlatmalı.
- Yeni müşteri kaydedilmeden önce status `PENDING` yapılmalı.
- Geçerli müşteri repository ile kaydedilmeli.

---

## CustomerServiceTest

```java
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void createCustomer_ShouldSaveCustomer_WhenIdentityNumberIsUnique() {
        // Arrange
        Customer customer = new Customer();
        customer.setFirstName("Aymina");
        customer.setLastName("Çakır");
        customer.setIdentityNumber("12345678910");

        when(customerRepository.existsByIdentityNumber("12345678910"))
                .thenReturn(false);

        when(customerRepository.save(customer))
                .thenReturn(customer);

        // Act
        Customer result = customerService.createCustomer(customer);

        // Assert
        assertNotNull(result);
        assertEquals("Aymina", result.getFirstName());
        assertEquals("Çakır", result.getLastName());
        assertEquals("PENDING", result.getStatus());
        assertNotNull(result.getCreatedAt());

        verify(customerRepository).existsByIdentityNumber("12345678910");
        verify(customerRepository).save(customer);
    }

    @Test
    void createCustomer_ShouldThrowException_WhenIdentityNumberAlreadyExists() {
        // Arrange
        Customer customer = new Customer();
        customer.setIdentityNumber("12345678910");

        when(customerRepository.existsByIdentityNumber("12345678910"))
                .thenReturn(true);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            customerService.createCustomer(customer);
        });

        assertEquals("Bu kimlik numarası ile kayıtlı müşteri zaten var.", exception.getMessage());

        verify(customerRepository).existsByIdentityNumber("12345678910");
        verify(customerRepository, never()).save(customer);
    }
}
```

---

## Testi Okurken Mantık

### Başarılı senaryo

```text
Arrange:
- Customer nesnesi hazırlanır.
- Repository TCKN yokmuş gibi ayarlanır.
- save çağrılınca customer dönsün denir.

Act:
- createCustomer çalıştırılır.

Assert:
- Sonuç null değil mi?
- Ad soyad doğru mu?
- Status PENDING oldu mu?
- createdAt doldu mu?
- Repository methodları çağrıldı mı?
```

### Hatalı senaryo

```text
Arrange:
- Customer hazırlanır.
- Repository TCKN varmış gibi ayarlanır.

Act & Assert:
- createCustomer çağrılınca RuntimeException fırlatmalı.
- save methodu hiç çağrılmamalı.
```

---

## Unit Test Yazarken Kurallar

1. Her test tek davranışı test etmeli.
2. Test adı açıklayıcı olmalı.
3. Arrange / Act / Assert ayrımı net olmalı.
4. Repository ve dış servisler mocklanmalı.
5. Test edilen class gerçek olmalı.
6. Testler birbirinden bağımsız olmalı.
7. Hem başarılı hem hatalı senaryo yazılmalı.
8. Unit test için gereksiz yere `@SpringBootTest` kullanılmamalı.

Unit test için genelde bu yeterlidir:

```java
@ExtendWith(MockitoExtension.class)
```

`@SpringBootTest` tüm uygulamayı ayağa kaldırır. Bu yüzden daha çok integration testlerde kullanılır.

---

## Kısa Özet

```text
Unit test = Küçük kod parçasını tek başına test etmek.
JUnit 5 = Test framework'ü.
Mockito = Mock oluşturmaya yarar.
@Mock = Sahte bağımlılık.
@InjectMocks = Test edilen class'a mockları verir.
Assert = Sonucu kontrol eder.
verify = Method çağrıldı mı kontrol eder.
Coverage = Testlerin kodun ne kadarını çalıştırdığıdır.
TDD = Önce test, sonra kod yazma yaklaşımı.
Arrange / Act / Assert = Testin yazım düzenidir.
```

---

