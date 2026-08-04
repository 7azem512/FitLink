# FitLink — Code Improvements Before Next Service

## 1. OtpType — Dead Values

`OtpType` has: `VERIFY`, `RESET`, `DEFAULT`, `DELETE`, `RESTORE`, `CHANGE_EMAIL`, `PASSWORD_RESET`

Only `VERIFY` and `PASSWORD_RESET` are actually used. `DEFAULT` was a bug we already fixed.
The rest (`RESET`, `DELETE`, `RESTORE`, `CHANGE_EMAIL`) are dead code — they add noise and confusion.

**Fix:** Remove unused enum values. If `CHANGE_EMAIL` is planned for later, add it when the feature is built.

```java
public enum OtpType {
    VERIFY,
    PASSWORD_RESET
}
```

---

## 2. ForgetPasswordService — Duplicates OTP Logic Already in OtpService

`ForgetPasswordService.sendResetOtp` manually builds and saves an OTP:
```java
otpRepository.deleteByUserAndOtpType(user, OtpType.PASSWORD_RESET);
String otpCode = String.format("%06d", new SecureRandom().nextInt(1_000_000));
otpRepository.save(OTP.builder()...build());
emailService.sendForgotPasswordOtp(...);
```

This is exactly what `OtpService.sendOtp(user, OtpType.PASSWORD_RESET)` does.

**Fix:** Inject `OtpService` into `ForgetPasswordService` and call `otpService.sendOtp(user, OtpType.PASSWORD_RESET)`.
Remove `OtpRepository` and `EmailService` dependencies from `ForgetPasswordService`.

---

## 3. OtpService.verifyEmail — Violates SRP

`OtpService.verifyEmail` does 4 things:
1. Validates OTP
2. Updates user status
3. Manually builds `FitLinkUserDetails` and sets `SecurityContext`
4. Generates and returns JWT tokens

Setting the `SecurityContext` and generating tokens is auth logic — it belongs in `authService`, not `OtpService`.

**Fix:** `verifyEmail` should return the `UserEntity` after verifying and activating. `authService` handles token generation.

```java
// OtpService
public UserEntity verifyEmail(String email, String otpCode) { ... }

// authService
public TokenResponse verifyEmail(String email, String otpCode) {
    UserEntity user = otpService.verifyEmail(email, otpCode);
    // build SecurityContext + generate tokens here
}
```

---

## 4. OTP Entity — Default Value on Field

```java
private OtpType otpType = OtpType.DEFAULT;
```

`OtpType.DEFAULT` no longer exists as a valid type (it was a bug). This default will cause a runtime error if an OTP is ever saved without explicitly setting the type.

**Fix:** Remove the default, make the field required, and rely on the builder to always pass the type explicitly.

```java
@Column(name = "otp_type", nullable = false)
private OtpType otpType;
```

---

## 5. AuthController — resend-otp Exposes OtpType.DEFAULT

```java
@RequestParam(defaultValue = "DEFAULT") OtpType otpType
```

`DEFAULT` is a dead/invalid type. The endpoint should only accept `VERIFY` or `PASSWORD_RESET`.

**Fix:** Remove the default value and make the client pass the type explicitly. Or better — split into two endpoints: one for email verification resend, one for password reset resend (each with a fixed type, no param needed).

---

## 6. SecurityConfig — CORS Config as Anonymous Class

The CORS config is written as an anonymous inner class inside a lambda:
```java
corsConfig.configurationSource(new CorsConfigurationSource() {
    @Override
    public CorsConfiguration getCorsConfiguration(...) { ... }
})
```

**Fix:** Extract to a `@Bean` method for readability and testability.

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    ...
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

---

## 7. jwtService — Lazy SecretKey Init is Not Thread-Safe

```java
public SecretKey getSecretKey() {
    if (secretKey == null) {
        secretKey = Keys.hmacShaKeyFor(...);
    }
    return secretKey;
}
```

Two threads could both see `secretKey == null` and both initialize it. In practice unlikely to cause a bug since the result is the same, but it's not correct.

**Fix:** Use `@PostConstruct` to initialize once on startup.

```java
@PostConstruct
private void initSecretKey() {
    secretKey = Keys.hmacShaKeyFor(JWT_SECRET_DEFAULT_VALUE.getBytes(StandardCharsets.UTF_8));
}
```

---

## 8. UserEntity — FetchType.EAGER on Roles

```java
@OneToMany(mappedBy = "user", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
private List<UserRole> roles;
```

`EAGER` means every time a `UserEntity` is loaded (including in the JWT filter on every request), it also loads all roles. This is an extra JOIN on every authenticated request.

**Fix:** Change to `FetchType.LAZY`. The places that need roles (`jwtService.generateAccessToken(user)`, `authService`, `CustomUserDetailsService`) already load the full entity in a transactional context where lazy loading works fine.

---

## 9. LoggingAspect — Masks Entire Args Object Instead of Sensitive Fields

```java
if (method.contains("password") || method.contains("login") || method.contains("register")) {
    return Arrays.stream(args).map(arg -> arg != null ? "***" : null).toArray();
}
```

This masks the entire request object. For `register`, you lose `email`, `userName`, `phone` — all useful for debugging. Only `password` fields need masking.

**Fix:** Check if the arg is a DTO and use reflection or a marker interface to mask only `@Sensitive` fields. Or at minimum, call `toString()` on the DTO with password fields excluded via `@ToString.Exclude` on the entity.

---

## Summary Table

| # | File | Issue | Priority |
|---|------|-------|----------|
| 1 | `OtpType.java` | Dead enum values | High |
| 2 | `ForgetPasswordService.java` | Duplicates OTP logic from `OtpService` | High |
| 3 | `OtpService.java` | `verifyEmail` does auth + token logic (SRP) | High |
| 4 | `OTP.java` | Default `OtpType.DEFAULT` on field | High |
| 5 | `AuthController.java` | `resend-otp` defaults to dead `OtpType.DEFAULT` | Medium |
| 6 | `SecurityConfig.java` | CORS as anonymous class | Low |
| 7 | `jwtService.java` | Lazy `SecretKey` init not thread-safe | Low |
| 8 | `UserEntity.java` | `FetchType.EAGER` on roles | Medium |
| 9 | `LoggingAspect.java` | Masks entire args instead of sensitive fields only | Low |
