# AGENTS.md

FitLink backend: Spring Boot 4.0.6, Java 21, Maven. Stateless JWT auth (access 1h / refresh 30d, `Constants.java`), PostgreSQL on Supabase, Supabase S3 storage, springdoc Swagger, Guava rate limiting.

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

## Role profile architecture

- Each selectable role (`TRAINEE`, `COACH`, `GYM`) has a profile entity in `entities/roles/` (`TraineeProfile`, `CoachProfile`, `GymProfile`) plus a matching repository in `repository/roles/`. The profile PK equals the user's `publicId` (UUID, assigned manually — no `@GeneratedValue`); the `user` relation stays FK `user_id → users.id`.
- Profile creation happens inside `authService.assignRole` (`/auth/select-role`), `@Transactional` with the role assignment. New role flow: if the user already holds the requested role → `ROLE_ALREADY_ASSIGNED` ("already have this role, login again"); otherwise create the profile, **delete the `UNASSIGNED` role row**, and add a `user_role` row for the requested role. A user can therefore accumulate multiple roles/profiles (each is `@OneToOne` on the user, so at most one profile per role).
- Enums live in domain subpackages under `utils/enums/` (`auth/`, `user/`, `coach/`, `gym/`, `trainee/`); DTOs under `dto/Auth/` domain subpackages (`role/`, `register/`, `login/`, `refresh/`, `password/`).

## Storage (Supabase S3)

- `service/fileStorage/StorageService` wraps the S3 client (bean in `config/SupabaseStorageConfig`, path-style). Upload flow: `POST /storage/upload` (multipart `file` + `folder`, **authenticated**) → service validates (empty/size/content-type) → stores at `{bucket}/{folder.path}/{uuid}{ext}` → returns the **public URL**; bulk via `POST /storage/upload-many` (returns `urls`).
- **Profile creation sends files in the same request**: `PATCH /auth/select-role` is a multipart endpoint (`@ModelAttribute @Valid SelectRoleRequest`). Profile data is form fields (`role`, `coachProfile.*`, `gymProfile.*`, `traineeProfile.*`); files are file parts named `traineeProfile.avatar`, `gymProfile.logo`, `gymProfile.cover`, `gymProfile.gallery` (repeatable → `gym_additional_images`), `coachProfile.cv`, `coachProfile.introVideo`. `authService.assignRole` uploads them via `StorageService` and persists the returned URLs on the entity (`profileImageUrl`, `logoUrl`, `coverImageUrl`, `additionalImages`, `cvUrl`, `introVideoUrl`). On any failure, `assignRole` deletes already-uploaded objects (no orphans).
- Folders + allowed content types are defined in `utils/enums/storage/StorageFolder` (`trainee/avatar`, `gym/logo`, `gym/cover`, `gym/gallery`, `coach/cv`, `coach/intro-video`).
- Delete: `StorageService.delete(key)` / `deleteByUrl(url)`. Storage errors use `ErrorCode`: `UNSUPPORTED_FILE_TYPE` (400), `FILE_TOO_LARGE` (413, also mapped from `MaxUploadSizeExceededException`), `STORAGE_UPLOAD_FAILED`/`STORAGE_DELETE_FAILED` (500), `UNSUPPORTED_MEDIA_TYPE` (415).
- Env vars: `SUPABASE_S3_ENDPOINT`, `SUPABASE_S3_REGION`, `SUPABASE_S3_ACCESS_KEY`, `SUPABASE_S3_SECRET_KEY`, `SUPABASE_S3_BUCKET`, `SUPABASE_URL` (used to build public URLs), plus multipart limits `MULTIPART_MAX_FILE_SIZE`/`MULTIPART_MAX_REQUEST_SIZE` (defaults 10MB/20MB) — see `.env.example`.

## Conventions & gotchas

- Service classes use a **lowercase first letter**: `jwtService`, `authService`, `otpService`. Keep it consistent (`StorageService` is the one deliberate exception).
- Errors: throw `exception/AppException(ErrorCode, message)`; add new codes to `exception/ErrorCode.java`. One response body via `exception/GlobalExceptionHandler` + `ErrorResponseWriter`.
- Public endpoints are **hardcoded** in `config/SecurityConfig.java` (auth flows, swagger, `/home/**`). Add new public routes there.
- Lombok is used throughout (`@RequiredArgsConstructor`, builders). `maven-compiler-plugin` has the annotation processor path configured.
- The `repository/` and `entities/` packages are split under `users/`; DTOs under `dto/Auth/`.
- Git: work happens on feature branches (current: `refactor-auth`). CI (`.github/workflows/docker-build.yml`) builds/pushes `ghcr.io/7azem512/fitlink-backend` only on pushes to the `mobile` branch.
