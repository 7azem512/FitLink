# AGENTS.md

FitLink backend: Spring Boot 4.0.6, Java 21, Maven. Stateless JWT auth (access 1h / refresh 30d, `Constants.java`), PostgreSQL on Supabase, springdoc Swagger, Guava rate limiting.

## Build & run

- Build/run with the Maven wrapper: `.\mvnw.cmd clean package -DskipTests` (Windows) / `./mvnw ...` (Unix). JDK 21 required. Java tooling lives in `target/` (gitignored).
- No env defaults — `src/main/resources/application.properties` is 100% `${VAR}` placeholders. Spring Boot does **not** auto-load `.env` (no dotenv dep); vars must come from the shell/IDE or `docker-compose.yml` (`env_file: .env`). See `.env.example` for the full set (`DATABASE_URL`, `DATABASE_USERNAME/PASSWORD`, `JWT_SECRET` ≥32 bytes, Gmail SMTP).
- No Flyway/Liquibase. Schema is `spring.jpa.hibernate.ddl-auto=update`. Roles are seeded at startup by `config/DataInitializer` — do not edit the DB by hand.

## Tests

- `.\mvnw.cmd test` runs JUnit 5 (Mockito). Single class: `.\mvnw.cmd test -Dtest=ForgetPasswordServiceTest`.
- **Known red on `refactor-auth`:** `ForgetPasswordServiceTest` still `@Mock`s `OtpRepository`/`EmailService`, but `ForgetPasswordService` now delegates to `OtpService` (package-private `sendOtp`/`consumeOtp`) → NPEs. Fix this test if you touch that service.
- `FitLinkApplicationTests` is `@SpringBootTest` (context load) and requires a reachable Postgres.

## Auth architecture (current state on `refactor-auth`)

- `tokenVersion` was **removed** (commit "remove token version"). JWT validation in `filters/JwtTokenValidatorFilter` → `service/auth/TokenAuthenticationService` is now fully stateless — no DB hit per request.
- `POST /auth/logout` is a **no-op stub**: only clears the `SecurityContext`; tokens stay valid until expiry. Real revocation is a planned Redis session feature (see `service/auth/authService.java:91`). Don't re-add tokenVersion.
- `selectRole` and password reset no longer invalidate old tokens.
- **`FLOWS.md`, `IMPROVEMENTS.md`, `PERFORMANCE_AUTH_IMPROVEMENTS.md` are stale** (still document `token_version`, token invalidation, old OTP flow). Trust the code; treat them as design history only.

## Conventions & gotchas

- Service classes use a **lowercase first letter**: `jwtService`, `authService`, `otpService`. Keep it consistent.
- Errors: throw `exception/AppException(ErrorCode, message)`; add new codes to `exception/ErrorCode.java`. One response body via `exception/GlobalExceptionHandler` + `ErrorResponseWriter`.
- Public endpoints are **hardcoded** in `config/SecurityConfig.java` (auth flows, swagger, `/home/**`). Add new public routes there.
- Lombok is used throughout (`@RequiredArgsConstructor`, builders). `maven-compiler-plugin` has the annotation processor path configured.
- The `repository/` and `entities/` packages are split under `users/`; DTOs under `dto/Auth/`.
- Git: work happens on feature branches (current: `refactor-auth`). CI (`.github/workflows/docker-build.yml`) builds/pushes `ghcr.io/7azem512/fitlink-backend` only on pushes to the `mobile` branch.
