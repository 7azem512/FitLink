# FitLink Backend — Comprehensive Project Plan

> **Last updated:** 2026-08-15
> **Stack:** Spring Boot 4.0.6, Java 21, Maven, PostgreSQL (Supabase), Supabase S3, Stateless JWT (1h access / 30d refresh), springdoc Swagger, Guava rate limiting.

---

## Current State — What's Done

### Auth & Registration
- Email/password registration with OTP verification (2-min cooldown)
- Google Sign-In (ID token exchange, find-or-create user)
- Login (email + password, checks `emailVerified`)
- Password reset (3-step: OTP → reset token → new password)
- Stateless JWT: access token (1h) + refresh token (30d), no DB hit per request
- `POST /auth/logout` is a **no-op stub** (tokens stay valid until expiry; Redis revocation is planned)

### Role Profiles
- Three selectable roles: `TRAINEE`, `COACH`, `GYM`
- Profile entities: `TraineeProfile`, `CoachProfile`, `GymProfile` — each PK = `user.publicId`
- Created at select-role time inside `authService.assignRole` (single `@Transactional` block)
- Profile creation uploads files (avatar, logo, cover, gallery, CV, intro video) to Supabase S3 via `StorageService`
- On failure, already-uploaded S3 objects are deleted (no orphan files)
- A user can accumulate multiple roles/profiles (each role at most once)

### Storage (Supabase S3)
- `POST /auth/select-role` — multipart form: profile data as form fields + files as file parts
- `POST /storage/upload` — single file upload (authenticated)
- `POST /storage/upload-many` — bulk upload (authenticated)
- `StorageFolder` enum: `trainee/avatar`, `gym/logo`, `gym/cover`, `gym/gallery`, `coach/cv`, `coach/intro-video`
- Validation: empty/size/content-type; error codes: `UNSUPPORTED_FILE_TYPE` (400), `FILE_TOO_LARGE` (413), `STORAGE_UPLOAD_FAILED` (500)

### Element Collections (Profile Sub-data)
- `GymProfile`: `workingDays` (Set\<DayOfWeek\>), `facilities` (List\<String\>), `additionalImages` (List\<String\>)
- `CoachProfile`: `specializations` (Set\<CoachSpecialization\>), `certifications` (List\<String\>)
- CRUD pattern: modify the collection → Hibernate deletes all + re-inserts (no PK on collection tables by design)
- `GymProfile.coaches` — bidirectional `@OneToMany` inverse side (FK: `coach_profile.current_gym_id`)

### Other
- `CleanupService` — scheduled hourly: removes expired OTPs and password reset tokens
- `EmailService` — async HTML emails via Gmail SMTP (OTP verification + password reset)
- Rate limiting: 200/hour for auth endpoints, 1000/min default
- `WebConfig` — custom `LocalTime` formatter (trims whitespace, accepts `HH:mm`)

---

## Phase 1 — Tech Debt & TODO Fixes

Priority: **HIGH → LOW**. Each is an independent, self-contained fix.

### 1.1 Fix `ForgetPasswordServiceTest` — **HIGH**
**Problem:** Still mocks `OtpRepository`/`EmailService` directly but `ForgetPasswordService` now delegates to `OtpService` (package-private `sendOtp`/`consumeOtp`) → NPEs.
**Fix:** Update test to mock `OtpService` instead; verify `sendOtp(user, PASSWORD_RESET)` is called.

### 1.2 Refactor `OtpService.verifyEmail` — SRP violation — **HIGH**
**Problem:** `verifyEmail()` does 4 things: consume OTP → set user flags → build `FitLinkUserDetails` + `SecurityContext` → generate tokens. This belongs in `authService`.
**Fix:** `OtpService.verifyEmail` returns `UserEntity` (verified user). The caller (`AuthController` or `authService`) builds the security context and tokens. Aligns with `authService.loginProcess` which already does the same work.

### 1.3 Add `@NotBlank` on `RefreshRequest.refreshToken` — **HIGH**
**Problem:** No validation on the field — empty/null refresh token reaches `authService` before being caught.
**Fix:** Add `@NotBlank` annotation.

### 1.4 Add phone format validation on `RegisterRequest.phone` — **MEDIUM**
**Problem:** `phone` has no regex/format constraint — accepts any string.
**Fix:** Add `@Pattern(regexp = "^(\\+?[0-9]{10,15})?$")` (optional, nullable).

### 1.5 Fix `CustomAuthenticationProvider` email verification TODO — **MEDIUM**
**Problem:** TODO comment says `//TODO: add check on email verification` — redundant with `authService` check but inconsistent.
**Fix:** Either add the check in the provider (fail-fast) or remove the TODO comment if service-level check is sufficient (it is).

### 1.6 Thread-safe `jwtService.getSecretKey()` — **LOW**
**Problem:** Lazy init of `SecretKey` is not thread-safe — multiple threads may compute the key concurrently.
**Fix:** Use `@PostConstruct` to eagerly initialize at startup (the key is derived from a fixed env var, so no reason for lazy init).

### 1.7 Remove stale test controllers — **LOW**
**Problem:** `TestController` and `HomeController` have comments `// will be deleted`.
**Fix:** Delete both controllers and their endpoints from `SecurityConfig` (`/home/**`, any `/test/**`).

### 1.8 Remove unused WebSocket dependency — **LOW**
**Problem:** `spring-boot-starter-websocket` is in `pom.xml` but no WebSocket code exists.
**Fix:** Remove from `pom.xml`.

### 1.9 Clean up stale documentation — **LOW**
**Problem:** `FLOWS.md`, `IMPROVEMENTS.md`, `PERFORMANCE_AUTH_IMPROVEMENTS.md` document removed/changed features.
**Fix:** Delete all three (AGENTS.md already marks them as stale). Update AGENTS.md to remove references to them.

---

## Phase 2 — Profile CRUD Endpoints

Users need to view, edit, and delete their profiles. Currently only `select-role` creates profiles. There are no GET/PATCH/DELETE endpoints.

### 2.1 Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/profiles/me` | Auth | Get own profile (based on JWT roles) |
| GET | `/profiles/{publicId}` | Public | View any user's public profile |
| PATCH | `/profiles/trainee` | Auth (TRAINEE) | Update trainee profile fields |
| PATCH | `/profiles/coach` | Auth (COACH) | Update coach profile fields |
| PATCH | `/profiles/gym` | Auth (GYM) | Update gym profile fields (including element collections) |
| DELETE | `/profiles/me` | Auth | Delete own profile + all roles + uploaded S3 files |
| PATCH | `/profiles/gym/coaches/{coachId}` | Auth (GYM owner) | Assign/remove a coach to this gym |

### 2.2 DTOs

```java
TraineeProfileUpdateRequest {
    MultipartFile avatar;        // optional, replaces old
    Gender gender;
    Double heightCm;
    Double weightKg;
    LocalDate birthday;
    TraineeGoal goal;
    ActivityLevel activityLevel;
    WorkingFrequency workingFrequency;
    PreferredTraining preferredTraining;
    WorkoutTime preferredWorkoutTime;
    String location;
}

CoachProfileUpdateRequest {
    MultipartFile cv;            // optional, replaces old
    MultipartFile introVideo;   // optional, replaces old
    String nationality;
    String city;
    Gender gender;
    Double heightCm;
    Double weightKg;
    LocalDate birthday;
    Integer yearsOfExperience;
    String languageSpoken;
    UUID currentGymId;           // null to unlink from gym
    Set<CoachSpecialization> specializations;
    List<String> certifications;
    String bio;
}

GymProfileUpdateRequest {
    MultipartFile logo;          // optional, replaces old
    MultipartFile cover;         // optional, replaces old
    List<MultipartFile> gallery; // optional, replaces gallery
    String gymName;
    GymType gymType;
    Integer establishYear;
    String description;
    String country, city, area;
    String googleMapsUrl, phoneNumber, whatsapp, websiteUrl;
    LocalTime openingTime, closingTime;
    Set<DayOfWeek> workingDays;  // replaced wholesale
    List<String> facilities;     // replaced wholesale
    String commercialRegistration, taxCard, ownerId;
}
```

### 2.3 Services

**PatchProfileService** (or extend `authService`):
- `updateTraineeProfile(UUID userId, TraineeProfileUpdateRequest)` — upload avatar if present, set fields, save
- `updateCoachProfile(UUID userId, CoachProfileUpdateRequest)` — upload cv/video if present, set fields, handle `currentGymId` unlink/link, save
- `updateGymProfile(UUID userId, GymProfileUpdateRequest)` — upload logo/cover/gallery if present, replace element collections wholesale (`clear() + addAll()`), save

**DeleteProfileService** (or extend `authService`):
- `deleteProfile(UUID userId)` — delete entity, delete all uploaded S3 files, delete `user_role` rows, delete element collection rows

### 2.4 Business Rules
- Profiles are owned by the user who created them (matching JWT `publicId`)
- Element collection fields (`workingDays`, `facilities`, `gallery`, `specializations`, `certifications`) are replaced wholesale on PATCH (clear + addAll pattern)
- Deleting a profile removes all uploaded S3 files via `StorageService.deleteByUrl`
- A user cannot delete a profile while they have active subscriptions (check `SubscriptionRepository`)
- Avatar/logo/cover: old S3 object is deleted when replaced with a new file
- `PATCH /profiles/gym/coaches/{coachId}`: sets `coachProfile.currentGymId = gymId` (or removes it)

### 2.5 Error Codes

```java
PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND),
PROFILE_OWNERSHIP_REQUIRED(HttpStatus.FORBIDDEN),
ACTIVE_SUBSCRIPTION_EXISTS(HttpStatus.CONFLICT),
```

---

## Phase 3 — Subscription Plans

Gyms and coaches offer subscription plans (monthly, quarterly, yearly). Trainees browse plans, subscribe, and pay.

### 3.1 Entities

**`Plan`** — a subscription offering by a gym or coach.

| Field | Type | Notes |
|---|---|---|
| id | UUID | PK (`@GeneratedValue`) |
| name | String | not null, max 100 (e.g. "Monthly Full Access") |
| description | String | `@Lob`, nullable |
| price | BigDecimal | not null |
| durationInDays | int | e.g. 30, 90, 365 |
| planType | PlanType | enum, not null |
| features | List\<String\> | `@ElementCollection` (what's included) |
| gym | GymProfile | `@ManyToOne(LAZY)` — who offers this plan |
| coach | CoachProfile | `@ManyToOne(LAZY)` — nullable (for coach-specific plans) |
| active | boolean | default true — soft-toggle |

**`Subscription`** — a trainee's active subscription to a plan.

| Field | Type | Notes |
|---|---|---|
| id | UUID | PK |
| trainee | TraineeProfile | `@ManyToOne(LAZY)`, not null |
| plan | Plan | `@ManyToOne(LAZY)`, not null |
| startDate | LocalDateTime | auto-set to now |
| endDate | LocalDateTime | calculated from `plan.durationInDays` |
| status | SubscriptionStatus | enum, default ACTIVE |
| paymentReference | String | nullable — payment gateway ID (future) |

### 3.2 Enums

```java
// utils/enums/subscription/PlanType.java
public enum PlanType { MONTHLY, QUARTERLY, YEARLY, CUSTOM }

// utils/enums/subscription/SubscriptionStatus.java
public enum SubscriptionStatus { ACTIVE, EXPIRED, CANCELLED }
```

### 3.3 Repositories

```java
PlanRepository extends JpaRepository<Plan, UUID>
    List<Plan> findByGymId(UUID gymId);
    List<Plan> findByCoachId(UUID coachId);
    List<Plan> findByActiveTrue();

SubscriptionRepository extends JpaRepository<Subscription, UUID>
    List<Subscription> findByTraineeId(UUID traineeId);
    Optional<Subscription> findByTraineeIdAndStatus(UUID traineeId, SubscriptionStatus status);
    boolean existsByTraineeIdAndPlanIdAndStatus(UUID traineeId, UUID planId, SubscriptionStatus status);
    void deleteByEndDateBefore(LocalDateTime cutoff);
```

### 3.4 DTOs

```java
PlanRequest {
    String name;
    String description;
    BigDecimal price;
    int durationInDays;
    PlanType planType;
    List<String> features;
}

PlanResponse {
    UUID id;
    String name;
    String description;
    BigDecimal price;
    int durationInDays;
    PlanType planType;
    List<String> features;
    UUID gymId;
    String gymName;
    UUID coachId;          // nullable
    boolean active;
}

SubscribeRequest {
    UUID planId;
}

SubscriptionResponse {
    UUID id;
    String planName;
    String gymName;
    LocalDateTime startDate;
    LocalDateTime endDate;
    SubscriptionStatus status;
}
```

### 3.5 Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/gyms/{gymId}/plans` | Auth (GYM owner) | Create a plan |
| GET | `/gyms/{gymId}/plans` | Public | List active plans for a gym |
| PATCH | `/plans/{planId}` | Auth (GYM owner) | Update a plan |
| DELETE | `/plans/{planId}` | Auth (GYM owner) | Soft-delete (set `active = false`) |
| POST | `/subscriptions` | Auth (TRAINEE) | Subscribe to a plan |
| GET | `/subscriptions/mine` | Auth (TRAINEE) | List my subscriptions |
| DELETE | `/subscriptions/{id}` | Auth (TRAINEE) | Cancel a subscription |
| GET | `/gyms/{gymId}/coaches` | Public | List coaches at a gym (uses `gym.getCoaches()`) |

### 3.6 Business Rules
- A trainee can have only **one active subscription per plan** (checked in service)
- `endDate` is calculated: `startDate + plan.durationInDays` days
- Subscribing to an already-active plan → `SUBSCRIPTION_ALREADY_ACTIVE` error
- Expired subscriptions are cleaned up by `CleanupService` (scheduled hourly)
- Payment integration is deferred (`paymentReference` nullable for now)
- Plans with active subscriptions cannot be deleted (soft-delete only)
- A gym owner can only create plans for their own gym

### 3.7 New Error Codes

```java
PLAN_NOT_FOUND(HttpStatus.NOT_FOUND),
PLAN_INACTIVE(HttpStatus.BAD_REQUEST),
SUBSCRIPTION_ALREADY_ACTIVE(HttpStatus.CONFLICT),
ACTIVE_SUBSCRIPTION_EXISTS(HttpStatus.CONFLICT),
PLAN_OWNERSHIP_REQUIRED(HttpStatus.FORBIDDEN),
```

---

## Execution Order

| Order | Phase | Est. Complexity | Depends On |
|---|---|---|---|
| 1 | Phase 1 — Tech Debt (all 9 items) | Low–Medium | Nothing |
| 2 | Phase 2 — Profile CRUD | Medium | Nothing |
| 3 | Phase 3 — Subscription Plans | Medium–High | Phase 2 (profiles must exist to subscribe) |

All three phases are **independent and can be worked on in parallel**, but the logical priority is: Tech Debt → Profile CRUD → Subscription Plans.

---

## Testing Strategy

- **Unit tests:** Mockito for services (follow existing `ForgetPasswordServiceTest` pattern)
- **Integration tests:** `@SpringBootTest` + `@AutoConfigureMockMvc` for controller endpoints (requires Postgres)
- **Standalone tests:** `ForgetPasswordServiceTest` — fix first (Phase 1.1)
- **Postman collection:** maintained manually for manual smoke tests (select-role + storage already tested)
- **CI:** `.github/workflows/docker-build.yml` — builds and pushes Docker image on push to `mobile` branch

---

## Stale Documentation (to be deleted)

| File | Status |
|---|---|
| `FLOWS.md` | Stale (documents removed `tokenVersion` feature) |
| `IMPROVEMENTS.md` | Stale (references completed items; valid items documented here) |
| `PERFORMANCE_AUTH_IMPROVEMENTS.md` | Stale (incorrect rate limit claims) |

All valid technical debt from these files has been migrated to Phase 1 of this plan.

---

## Environment Variables

All required env vars (no defaults, no `.env` auto-load):

```bash
# Database
DATABASE_URL=jdbc:postgresql://...
DATABASE_USERNAME=...
DATABASE_PASSWORD=...

# JWT
JWT_SECRET=at-least-32-random-bytes

# Google
GOOGLE_CLIENT_ID=...

# Mail
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...

# Supabase S3
SUPABASE_S3_ENDPOINT=https://YOUR_REF.supabase.co/storage/v1/s3
SUPABASE_S3_REGION=us-east-1
SUPABASE_S3_ACCESS_KEY=...
SUPABASE_S3_SECRET_KEY=...
SUPABASE_S3_BUCKET=...
SUPABASE_URL=https://YOUR_REF.supabase.co

# Multipart
MULTIPART_MAX_FILE_SIZE=10MB
MULTIPART_MAX_REQUEST_SIZE=20MB
SUPABASE_STORAGE_MAX_FILE_SIZE_BYTES=10485760

# Hibernate
HIBERNATE_DDL_AUTO=update
```
