# FitLink – Auth & Entity Flow Documentation

---

## Database Entities

### `app_user` (UserEntity)
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | Auto-generated |
| public_id | UUID | Unique, immutable, auto-generated on persist |
| email | VARCHAR(255) | Unique |
| phone | VARCHAR(20) | Nullable |
| user_name | VARCHAR(50) | Min 3 chars |
| password_hash | VARCHAR | BCrypt encoded |
| status | ENUM | `PENDING` → `ACTIVE` |
| email_verified | BOOLEAN | Default `false` |
| token_version | INT | Starts at `1`, incremented on logout / password reset / role change |

### `roles`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | Auto-generated |
| role_name | VARCHAR(30) | Enum value stored as string |

Seeded automatically on startup via `DataInitializer`:

| id | role_name |
|---|---|
| 1 | UNASSIGNED |
| 2 | TRAINEE |
| 3 | COACH |
| 4 | GYM |
| 5 | ADMIN |
| 6 | SYSTEM |
| 7 | USER |

### `user_role`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | Auto-generated |
| user_id | FK → app_user | |
| role_id | FK → roles | |
| Unique constraint | (user_id, role_id) | No duplicate role per user |

### `otp`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| otp_code | VARCHAR | 6-digit numeric string |
| expires_at | DATETIME | |
| user_id | FK → app_user | |
| otp_type | ENUM | `DEFAULT` (email verify) / `PASSWORD_RESET` |
| pending_email | VARCHAR | Nullable |

### `password_reset_token`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| token_hash | VARCHAR(64) | SHA-256 hash of the raw token |
| user_id | FK → app_user | |
| created_at | DATETIME | |
| expires_at | DATETIME | 10 minutes from creation |
| used_at | DATETIME | Nullable – set when token is consumed |

---

## Roles

| Role | Description |
|---|---|
| `UNASSIGNED` | Default role assigned at registration. User must call select-role to proceed. |
| `TRAINEE` | Regular fitness user / client |
| `COACH` | Personal trainer / coach |
| `GYM` | Gym owner / facility |
| `ADMIN` | Platform administrator |
| `SYSTEM` | Internal system use |
| `USER` | Generic authenticated user (used in JWT after email verify, before role selection) |

> Roles `ADMIN`, `SYSTEM`, `UNASSIGNED`, and `USER` **cannot** be selected by the user via the select-role endpoint.

---

## JWT Token Structure

Both access and refresh tokens carry the same claims:

| Claim | Value |
|---|---|
| `id` | User DB id |
| `email` | User email |
| `userName` | Display name |
| `authorities` | Comma-separated role strings e.g. `ROLE_TRAINEE` |
| `tokenVersion` | Must match `token_version` in DB, otherwise token is rejected |

The `JwtTokenValidatorFilter` validates every request by:
1. Extracting the token from the `Authorization: Bearer` header
2. Parsing claims and checking `tokenVersion` against the DB
3. Rejecting if mismatch (covers logout and password reset invalidation)

---

## Flow 1 – Register

```
POST /auth/register
Body: { userName, email, phone, password, confirmPassword }
```

1. Check email not already registered → `DUPLICATE_EMAIL (409)` if exists
2. Check `password == confirmPassword` → `PASSWORD_MISMATCH (400)` if not
3. Create `UserEntity` with `status=PENDING`, `emailVerified=false`, `tokenVersion=1`
4. Save user
5. Fetch `UNASSIGNED` role from `roles` table
6. Create `UserRole` linking user ↔ UNASSIGNED role → save
7. Generate 6-digit OTP, save to `otp` table with `otpType=DEFAULT`, expires in **10 minutes**
8. Send OTP email
9. Return `201` with message

**Errors:** `DUPLICATE_EMAIL`, `PASSWORD_MISMATCH`, `VALIDATION_ERROR`, `MALFORMED_REQUEST`

---

## Flow 2 – Verify Email OTP

```
POST /auth/verify-otp?email=&otpCode=
```

1. Find user by email → `USER_NOT_FOUND (404)` if not found
2. Find OTP by user + code + `otpType=DEFAULT` → `INVALID_OTP (400)` if not found
3. Check OTP not expired → `OTP_EXPIRED (410)` if expired
4. Set `emailVerified=true`, `status=ACTIVE` → save user
5. Delete OTP
6. Build `FitLinkUserDetails` with `ROLE_USER` authority, set in `SecurityContext`
7. Generate access token + refresh token
8. Return `200` with `{ accessToken, refreshToken, userName, role: "ROLE_USER" }`

> After this step the user has `ROLE_USER` in their token and `UNASSIGNED` in the DB. They must call select-role next.

**Errors:** `USER_NOT_FOUND`, `INVALID_OTP`, `OTP_EXPIRED`

---

## Flow 3 – Resend OTP

```
POST /auth/resend-otp?email=&otpType=DEFAULT
```

1. Find user by email (if not found → return success silently, no email leak)
2. Check cooldown: if last OTP was sent less than **2 minutes** ago → `OTP_RESEND_COOLDOWN (429)`
3. Delete existing OTP for user + type
4. Generate new 6-digit OTP, save with `otpType`, expires in **10 minutes**
5. Send OTP email
6. Return `200` with message

**Errors:** `OTP_RESEND_COOLDOWN`, `VALIDATION_ERROR`

---

## Flow 4 – Login

```
POST /auth/login
Body: { email, password }
```

1. Authenticate via `AuthenticationManager` → `BAD_CREDENTIALS (401)` if fails
2. Find user by email → `BAD_CREDENTIALS (401)` if not found
3. Check `emailVerified == true` → `EMAIL_NOT_VERIFIED (403)` if not
4. Set authentication in `SecurityContext`
5. Generate access token + refresh token
6. Extract `userName` and `role` from access token claims
7. Return `200` with `{ accessToken, refreshToken, userName, role }`

**Errors:** `BAD_CREDENTIALS`, `EMAIL_NOT_VERIFIED`, `VALIDATION_ERROR`

---

## Flow 5 – Select Role

```
PATCH /auth/select-role   [Requires Bearer token]
Body: { role: "TRAINEE" | "COACH" | "GYM" }
```

1. Get current user from `SecurityContext`
2. Find user in DB → `USER_NOT_FOUND (404)` if not found
3. Check user has `UNASSIGNED` role → `ROLE_ALREADY_ASSIGNED (409)` if not UNASSIGNED
4. Parse requested role string → `INVALID_ROLE (400)` if invalid enum value
5. Reject if role is `ADMIN`, `SYSTEM`, `UNASSIGNED`, or `USER` → `ROLE_NOT_ALLOWED (403)`
6. Fetch the target `Role` entity from `roles` table by `roleCode`
7. Update `UserRole.role` to the new role entity → save
8. Increment `tokenVersion` → save user (invalidates all previous tokens)
9. Generate new access token + refresh token using updated user
10. Return `200` with `{ role, accessToken, refreshToken, message }`

**Errors:** `UNAUTHORIZED`, `USER_NOT_FOUND`, `ROLE_ALREADY_ASSIGNED`, `INVALID_ROLE`, `ROLE_NOT_ALLOWED`

---

## Flow 6 – Refresh Token

```
POST /auth/refresh-token
Body: { refreshToken }
```

1. Validate refresh token signature and expiry → `INVALID_REFRESH_TOKEN (401)` if invalid
2. Extract claims: `id`, `email`, `userName`, `authorities`
3. Check user exists by email → `INVALID_REFRESH_TOKEN (401)` if not found
4. Build new access token with same claims + new `issuedAt` / `expiration`
5. Return `200` with `{ newAccessToken }`

> Refresh token is **not rotated**. The same refresh token remains valid until logout or password reset.

**Errors:** `INVALID_REFRESH_TOKEN`, `MALFORMED_REQUEST`

---

## Flow 7 – Logout

```
POST /auth/logout   [Requires Bearer token]
```

1. Get current user from `SecurityContext`
2. Find user in DB → `USER_NOT_FOUND (404)` if not found
3. Increment `tokenVersion` → save (invalidates all existing tokens across all devices)
4. Clear `SecurityContext`
5. Return `200` with message

**Errors:** `UNAUTHORIZED`, `USER_NOT_FOUND`

---

## Flow 8 – Forgot Password (3 steps)

### Step 1 – Request OTP
```
POST /forget-password
Body: { email }
```

1. Normalize email (trim + lowercase)
2. Find user by email (if not found → return success silently)
3. Delete any existing `PASSWORD_RESET` OTP for user
4. Generate 6-digit OTP, save with `otpType=PASSWORD_RESET`, expires in **5 minutes**
5. Send reset OTP email
6. Return `200` with generic message (same response whether email exists or not)

---

### Step 2 – Verify OTP
```
POST /forget-password/verify-otp
Body: { email, otpCode }
```

1. Find user by email → `INVALID_OTP (400)` if not found (no email leak)
2. Find OTP by user + code + `otpType=PASSWORD_RESET` → `INVALID_OTP (400)` if not found
3. Check OTP not expired → delete OTP + `OTP_EXPIRED (410)` if expired
4. Delete OTP (consumed)
5. Delete any existing `PasswordResetToken` for user
6. Generate 32-byte secure random token (raw)
7. Hash raw token with **SHA-256** → store only the hash in DB
8. Save `PasswordResetToken` with `expiresAt = now + 10 minutes`
9. Return `200` with `{ resetToken (raw), expiresIn: 600 }`

---

### Step 3 – Reset Password
```
POST /forget-password/reset
Body: { resetToken, newPassword, confirmPassword }
```

1. Check `newPassword == confirmPassword` → `PASSWORD_MISMATCH (400)` if not
2. Hash `resetToken` with SHA-256
3. Find `PasswordResetToken` by hash → `INVALID_RESET_TOKEN (400)` if not found
4. Check not expired → delete token + `RESET_TOKEN_EXPIRED (410)` if expired
5. Check not already used → `RESET_TOKEN_USED (409)` if used
6. BCrypt encode new password → update `user.passwordHash`
7. Increment `tokenVersion` (invalidates all existing JWT tokens)
8. Save user
9. Delete reset token
10. Return `200` with message

**Errors:** `PASSWORD_MISMATCH`, `INVALID_RESET_TOKEN`, `RESET_TOKEN_EXPIRED`, `RESET_TOKEN_USED`

---

## Token Invalidation Summary

| Action | tokenVersion incremented? | Effect |
|---|---|---|
| Logout | ✅ | All tokens on all devices invalid |
| Select Role | ✅ | Previous tokens invalid, new tokens returned |
| Reset Password | ✅ | All tokens on all devices invalid |
| Register / Login / Verify OTP | ❌ | No invalidation |

---

## Error Codes Reference

| Code | HTTP | Trigger |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Bean validation failure |
| `MALFORMED_REQUEST` | 400 | Bad JSON / wrong type |
| `PASSWORD_MISMATCH` | 400 | password ≠ confirmPassword |
| `INVALID_OTP` | 400 | OTP not found or wrong |
| `INVALID_ROLE` | 400 | Unknown role string |
| `INVALID_RESET_TOKEN` | 400 | Reset token hash not found |
| `BAD_CREDENTIALS` | 401 | Wrong email or password |
| `UNAUTHORIZED` | 401 | Missing / invalid JWT |
| `INVALID_REFRESH_TOKEN` | 401 | Expired or invalid refresh token |
| `EMAIL_NOT_VERIFIED` | 403 | Login before verifying email |
| `ROLE_NOT_ALLOWED` | 403 | Tried to select ADMIN/SYSTEM/etc. |
| `FORBIDDEN` | 403 | Access denied |
| `USER_NOT_FOUND` | 404 | User not in DB |
| `DUPLICATE_EMAIL` | 409 | Email already registered |
| `ROLE_ALREADY_ASSIGNED` | 409 | User already has a non-UNASSIGNED role |
| `RESET_TOKEN_USED` | 409 | Reset token already consumed |
| `DATA_INTEGRITY_VIOLATION` | 409 | DB constraint violation |
| `OTP_EXPIRED` | 410 | OTP past expiry time |
| `RESET_TOKEN_EXPIRED` | 410 | Reset token past expiry time |
| `OTP_RESEND_COOLDOWN` | 429 | Resend within 2-minute cooldown |
| `INTERNAL_ERROR` | 500 | Unhandled server exception |
