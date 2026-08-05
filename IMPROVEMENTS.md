# FitLink — Improvements & Technical Debt

## Status of Previous Items

| # | Issue | Status |
|---|-------|--------|
| 1 | `OtpType` dead values | ✅ Fixed |
| 2 | `ForgetPasswordService` duplicates OTP logic | ✅ Fixed |
| 3 | `OtpService.verifyEmail` does auth logic (SRP) | ⏳ Pending |
| 4 | `OTP` entity default `OtpType.DEFAULT` | ✅ Fixed |
| 5 | `resend-otp` defaults to dead `OtpType.DEFAULT` | ✅ Fixed |
| 6 | CORS as anonymous class in `SecurityConfig` | ⏳ Pending |
| 7 | `jwtService` lazy `SecretKey` init not thread-safe | ⏳ Pending |
| 8 | `UserEntity` `FetchType.EAGER` on roles | ✅ Fixed |
| 9 | `LoggingAspect` masks entire args | ⏳ Pending |

---

## New Findings

---

### 1. tokenVersion — DB Hit on Every Request (Design Discussion)

**الوضع الحالي:**
كل request بيعمل DB call عشان يجيب الـ user ويتحقق من الـ `tokenVersion`. ده بيكسر فكرة الـ JWT الأساسية إن الـ token stateless ومش محتاج DB.

**ليه اتعمل كده؟**
عشان نقدر نعمل logout فوري — لما الـ user يعمل logout، الـ `tokenVersion` بيتزود بـ 1 فكل الـ tokens القديمة بتبطل.

**المشكلة:**
- DB hit على كل request = bottleneck على scale
- بيكسر الـ stateless nature بتاعة الـ JWT

**البدائل:**

**Option A — Token Blocklist (Redis)**
بدل ما تشيك على كل request، لما الـ user يعمل logout بس تحط الـ `jti` (token ID) في Redis بـ TTL = باقي عمر الـ token. الـ filter يشيك على Redis بس — أسرع بكتير من DB.

```
logout → redis.set(jti, "revoked", ttl=remaining_expiry)
filter → if redis.exists(jti) → reject
```

**Option B — Short-lived Access Tokens**
خلي الـ access token يعيش 15 دقيقة بدل ساعة. لو الـ user عمل logout، أقصى حاجة هو 15 دقيقة والـ token القديم يبطل لوحده. مش محتاج DB ولا Redis.

**Option C — الوضع الحالي (tokenVersion في DB)**
بسيط، مش محتاج Redis، بيضمن logout فوري. المشكلة بس الـ performance على scale.

**التوصية:**
- دلوقتي في المرحلة دي → خلي الـ tokenVersion زي ما هو، بسيط وشغال
- لما تحتاج scale → انتقل لـ Redis blocklist

---

### 2. DTOs — Validations ناقصة أو غلط

**`RefreshRequest`** — مفيش `@NotBlank` على `refreshToken`:
```java
// الحالي — مفيش validation
private String refreshToken;

// الصح
@NotBlank(message = "Refresh token is required")
private String refreshToken;
```

**`RegisterRequest`** — الـ `phone` مفيش validation على format:
```java
// ممكن تضيف
@Pattern(regexp = "^[0-9]{10,15}$", message = "Invalid phone number")
private String phone;
```

**`RegisterRequest`** — الـ `confirmPassword` مفيش `@Size` — ممكن حد يبعت password صح بس confirmPassword فارغ أو أقل من 8 حروف ويعدي الـ `@NotBlank`:
```java
@Size(min = 8, max = 16, message = "Password must be between 8 and 16 characters")
private String confirmPassword;
```

---

### 3. Entities — Validations ناقصة

**`UserEntity`** — الـ `email` مفيش `@Email` constraint على مستوى الـ entity، بس موجود في الـ DTO بس. لو في مستقبل في حاجة بتعمل save للـ user من غير الـ DTO (زي migration أو seeding) هيعدي من غير validation.

**`UserEntity`** — الـ `phone` مفيش length constraint في الـ column definition:
```java
// الحالي
@Column(name = "phone", length = 20)

// كويس، بس ممكن تضيف DB-level check constraint في migration
```

**`PasswordResetToken`** — `createdAt` بيتحط يدوياً في الـ service:
```java
.createdAt(LocalDateTime.now())
```
بس الـ entity مش بيورث `AuditEntity` — يعني لو نسيت تحطه هيبقى null. الأحسن إما يورث `AuditEntity` أو يستخدم `@PrePersist`.

---

### 4. Entity Relationships — ملاحظات

**`UserRole` extends `AuditEntity`** — join table بتورث `createdAt`, `updatedAt`, `createdBy`, `updatedBy`. الـ `createdAt` مفيد (تعرف امتى اتعين الـ role)، بس `updatedBy` و `createdBy` مش هيتملوا صح لأن الـ user مش logged in وقت الـ register. مش مشكلة كبيرة بس خليها في بالك.

**`OTP` — `pendingEmail` field غير مستخدم:**
```java
private String pendingEmail;
```
ده field موجود في الـ entity بس مش بيتستخدم في أي حاجة. إما تشيله أو تستخدمه في الـ `CHANGE_EMAIL` flow لما تبنيه.

**`UserEntity` — `@Inheritance(strategy = InheritanceType.JOINED)`** — ده معناه إنك بتخطط إن في entities تانية هترث من `UserEntity` (زي `TraineeProfile`, `CoachProfile`). ده design صح لو ده القصد، بس لو مش هتعمل inheritance خليه `@Entity` عادي.

---

### 5. OtpService.verifyEmail — لسه بتعمل Auth Logic (SRP)

ده من الـ items القديمة اللي لسه pending. `verifyEmail` دلوقتي بتعمل:
1. validate OTP ✅ شغلتها
2. update user status ✅ شغلتها
3. بتبني `FitLinkUserDetails` يدوياً وتحطه في `SecurityContext` ❌ مش شغلتها
4. بتجنرت tokens وترجعهم ❌ مش شغلتها

**Fix:**
```java
// OtpService — بترجع UserEntity بس
public UserEntity verifyEmail(String email, String otpCode) { ... }

// authService — بتعمل tokens
public TokenResponse verifyEmail(String email, String otpCode) {
    UserEntity user = otpService.verifyEmail(email, otpCode);
    String accessToken  = jwtService.generateAccessToken(user);
    String refreshToken = jwtService.generateRefreshToken(user);
    return TokenResponse.builder()...build();
}
```

---

### 6. SecurityConfig — CORS كـ Anonymous Class

لسه pending من قبل. استخرجه لـ `@Bean`:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("http://localhost:*", "https://yourdomain.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

---

### 7. jwtService — SecretKey Init مش Thread-Safe

لسه pending. استبدل الـ lazy init بـ `@PostConstruct`:

```java
@PostConstruct
private void initSecretKey() {
    secretKey = Keys.hmacShaKeyFor(JWT_SECRET_DEFAULT_VALUE.getBytes(StandardCharsets.UTF_8));
}
```

---

## Summary Table (Full)

| # | File | Issue | Priority | Status |
|---|------|-------|----------|--------|
| 1 | `TokenAuthenticationService` | DB hit على كل request بسبب tokenVersion | Medium | ⏳ Acceptable now, Redis later |
| 2 | `RefreshRequest` | مفيش `@NotBlank` على `refreshToken` | High | ⏳ Pending |
| 3 | `RegisterRequest` | مفيش validation على `phone` format و `confirmPassword` size | Medium | ⏳ Pending |
| 4 | `PasswordResetToken` | `createdAt` بيتحط يدوياً، مش بيورث `AuditEntity` | Low | ⏳ Pending |
| 5 | `OTP` entity | `pendingEmail` field غير مستخدم | Low | ⏳ Pending |
| 6 | `OtpService.verifyEmail` | بتعمل auth + token logic (SRP) | High | ⏳ Pending |
| 7 | `SecurityConfig` | CORS كـ anonymous class | Low | ⏳ Pending |
| 8 | `jwtService` | `SecretKey` lazy init مش thread-safe | Low | ⏳ Pending |
| 9 | `LoggingAspect` | بتماسك كل الـ args مش الـ sensitive fields بس | Low | ⏳ Pending |
