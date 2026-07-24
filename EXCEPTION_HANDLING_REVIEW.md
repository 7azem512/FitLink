# Exception Handling Review

## Purpose

This document records the exception-handling changes made to the Auth flows.
The goal was to replace business `RuntimeException` cases with typed errors,
return the correct HTTP status, and use one response body for validation,
business, and Security errors. A second safety pass added privacy protections,
request metadata, and matching Swagger/OpenAPI documentation.

## Scope Kept Intact

- Endpoint URLs are unchanged.
- Successful response bodies are unchanged.
- Authentication, JWT, OTP, database, and email flows remain unchanged except
  for the required privacy behavior: unknown login emails now share the same
  response as wrong passwords, and unknown resend-OTP emails share the normal
  resend success response without an OTP or email being created.
- No DTO validation annotations or `@Valid` usage were changed.
- No password or phone validation rules were changed.
- No database schema, migration, test, dependency, package, or service-name change was made.
- `EmailService`, email templates, async behavior, and auditing were not modified.

## Unified Error Response

All handled errors now use the same JSON structure:

```json
{
  "timestamp": "2026-07-24T01:30:00",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/auth/register",
  "errors": {
    "email": "Invalid email format"
  }
}
```

Fields:

- `timestamp`: ISO-8601 local server time at which the error was created.
- `status`: HTTP status returned by the API.
- `code`: stable application error code from `ErrorCode`.
- `message`: client-facing summary of the failure.
- `path`: request URI that produced the error.
- `errors`: optional field-level details for validation and malformed requests.

`errors` is omitted when it is empty.

## Error Flow

```text
Business failure
  -> AppException(ErrorCode, message)
  -> GlobalExceptionHandler
  -> ErrorResponse(timestamp, status, code, message, path, optional errors)
  -> HTTP response with ErrorCode status
```

`DuplicateEmailException` now extends `AppException`, so it follows the same
path without needing a separate duplicate-email handler.

## Error Code and HTTP Status Map

| Error code | HTTP status | Meaning |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 Bad Request | DTO or request validation failure. |
| `MALFORMED_REQUEST` | 400 Bad Request | Invalid JSON, unsupported field, or invalid enum value. |
| `DATA_INTEGRITY_VIOLATION` | 409 Conflict | Safe generic database conflict response. |
| `NOT_FOUND` | 404 Not Found | Generic JPA entity not found. |
| `DUPLICATE_EMAIL` | 409 Conflict | An account already uses the submitted email. |
| `PASSWORD_MISMATCH` | 400 Bad Request | Password and confirmation differ. |
| `BAD_CREDENTIALS` | 401 Unauthorized | Invalid login credentials. |
| `EMAIL_NOT_VERIFIED` | 403 Forbidden | Login credentials are valid but the email is not verified. |
| `UNAUTHORIZED` | 401 Unauthorized | Missing or invalid authenticated principal. |
| `FORBIDDEN` | 403 Forbidden | Authenticated caller does not have access. |
| `USER_NOT_FOUND` | 404 Not Found | Expected user record does not exist. |
| `INVALID_OTP` | 400 Bad Request | OTP is absent or incorrect. |
| `OTP_EXPIRED` | 410 Gone | OTP exists but has expired. |
| `OTP_RESEND_COOLDOWN` | 429 Too Many Requests | A replacement OTP was requested before the cooldown ends. |
| `INVALID_REFRESH_TOKEN` | 401 Unauthorized | Refresh token is invalid or its user no longer exists. |
| `INVALID_RESET_TOKEN` | 400 Bad Request | Password-reset token is missing or invalid. |
| `RESET_TOKEN_EXPIRED` | 410 Gone | Password-reset token has expired. |
| `RESET_TOKEN_USED` | 409 Conflict | Password-reset token was already used. |
| `INVALID_ROLE` | 400 Bad Request | Submitted role value is unsupported. |
| `ROLE_ALREADY_ASSIGNED` | 409 Conflict | The user already selected a role. |
| `ROLE_NOT_ALLOWED` | 403 Forbidden | A reserved or prohibited role was requested. |
| `INTERNAL_ERROR` | 500 Internal Server Error | Unexpected unhandled exception. |

## Auth Behavior Mapping

| Flow | Scenario | Final behavior |
| --- | --- | --- |
| Register | Duplicate email | `DUPLICATE_EMAIL` - 409 |
| Register | Password confirmation mismatch | `PASSWORD_MISMATCH` - 400 |
| Login | Unknown email or wrong password | Same `BAD_CREDENTIALS` - 401 response with `Invalid email or password`. |
| Login | Email not verified | `EMAIL_NOT_VERIFIED` - 403 |
| Verify email OTP | User not found | `USER_NOT_FOUND` - 404 |
| Verify email OTP | Invalid OTP | `INVALID_OTP` - 400 |
| Verify email OTP | Expired OTP | `OTP_EXPIRED` - 410 |
| Resend OTP | Unknown email | Same 200 success response as an existing email; no OTP is created and no email is sent. |
| Resend OTP | Cooldown active | `OTP_RESEND_COOLDOWN` - 429 |
| Logout | Invalid principal | `UNAUTHORIZED` - 401 |
| Logout | User not found | `USER_NOT_FOUND` - 404 |
| Refresh token | Invalid, expired, or no-longer-associated user | `INVALID_REFRESH_TOKEN` - 401 |
| Select role | Invalid principal | `UNAUTHORIZED` - 401 |
| Select role | User not found | `USER_NOT_FOUND` - 404 |
| Select role | Role already assigned | `ROLE_ALREADY_ASSIGNED` - 409 |
| Select role | Invalid role value | `INVALID_ROLE` - 400 |
| Select role | Reserved role selected | `ROLE_NOT_ALLOWED` - 403 |
| Password reset OTP | Invalid OTP | `INVALID_OTP` - 400 |
| Password reset OTP | Expired OTP | `OTP_EXPIRED` - 410 |
| Request password reset | Unknown email | Same generic 200 success response as an existing email; no OTP is created and no email is sent. |
| Password reset | Password confirmation mismatch | `PASSWORD_MISMATCH` - 400 |
| Password reset | Invalid reset token | `INVALID_RESET_TOKEN` - 400 |
| Password reset | Expired reset token | `RESET_TOKEN_EXPIRED` - 410 |
| Password reset | Already-used reset token | `RESET_TOKEN_USED` - 409 |

## Global Exception Handler Changes

- `AppException` is handled centrally and uses the status from `ErrorCode`.
- Bean validation still returns 400, now with `VALIDATION_ERROR` and field errors.
- Malformed JSON and invalid enum input still return 400, now with
  `MALFORMED_REQUEST`.
- `BadCredentialsException` remains supported and returns 401 with
  `BAD_CREDENTIALS`.
- `UsernameNotFoundException` returns 404 with `USER_NOT_FOUND`.
- Database constraint failures return 409 with `DATA_INTEGRITY_VIOLATION` and
  the safe message `A data conflict occurred.`
- Unexpected exceptions now return a non-leaking 500 response with
  `INTERNAL_ERROR`, instead of returning 409 and the internal exception message.
- Expected business errors are logged at WARN without stack traces; unexpected
  errors are logged at ERROR with a stack trace. Request bodies, passwords,
  OTPs, tokens, Authorization headers, and mail credentials are not logged.

## Security 401 and 403 Responses

Three previously inconsistent Security responses now emit `ErrorResponse`
through the shared `ErrorResponseWriter`:

| Source | Status | Error code |
| --- | --- | --- |
| `CustomAuthenticationEntryPoint` | 401 | `UNAUTHORIZED` |
| `JwtTokenValidatorFilter` for invalid JWT | 401 | `UNAUTHORIZED` |
| `CustomAccessDeniedHandler` | 403 | `FORBIDDEN` |

The existing response headers in the two custom Security handlers were retained.
The JWT filter only catches expected JWT parsing/input errors, writes one 401,
then returns before the filter chain can continue. Unexpected repository or
programming failures are not converted to 401.

## Files Created

| File | Reason |
| --- | --- |
| `src/main/java/com/project/FitLink/exception/ErrorCode.java` | Defines the stable application error codes and their HTTP statuses. |
| `src/main/java/com/project/FitLink/exception/AppException.java` | Carries an `ErrorCode` and client-safe message for business failures. |
| `src/main/java/com/project/FitLink/exception/ErrorResponseWriter.java` | Writes the shared serialized error response for Security entry points and the JWT filter. |

## Files Modified

| File | Reason |
| --- | --- |
| `src/main/java/com/project/FitLink/exception/ErrorResponse.java` | Replaces the old map-only response with the unified error body. |
| `src/main/java/com/project/FitLink/exception/GlobalExceptionHandler.java` | Produces unified errors for application, validation, JSON, persistence, authentication, and unexpected exceptions. |
| `src/main/java/com/project/FitLink/exception/exceptions/DuplicateEmailException.java` | Integrates duplicate-email failures with `AppException`. |
| `src/main/java/com/project/FitLink/exception/authHandle/CustomAuthenticationEntryPoint.java` | Aligns Security 401 output with the unified response. |
| `src/main/java/com/project/FitLink/exception/authHandle/CustomAccessDeniedHandler.java` | Aligns Security 403 output with the unified response. |
| `src/main/java/com/project/FitLink/filters/JwtTokenValidatorFilter.java` | Aligns invalid-JWT 401 output with the unified response. |
| `src/main/java/com/project/FitLink/service/authService.java` | Converts login, logout, refresh-token, and role-selection business failures to typed errors. |
| `src/main/java/com/project/FitLink/service/UserService.java` | Converts registration password mismatch to a typed error. |
| `src/main/java/com/project/FitLink/service/OtpService.java` | Converts OTP lookup, expiry, user, and cooldown failures to typed errors. |
| `src/main/java/com/project/FitLink/service/ForgetPasswordService.java` | Converts password-reset OTP and token failures to typed errors. |
| `src/main/java/com/project/FitLink/controller/auth/AuthController.java` | Documents the real Auth error scenarios and final `ErrorResponse` examples. |
| `src/main/java/com/project/FitLink/controller/auth/ForgetPasswordController.java` | Documents the password-reset error scenarios and final `ErrorResponse` examples. |
| `EXCEPTION_HANDLING_REVIEW.md` | Records both implementation passes, final Swagger scenarios, examples, and verification results. |

## Intentionally Unchanged Exception

`ForgetPasswordService.sha256` still throws `RuntimeException` if the Java
runtime does not provide SHA-256. This is an infrastructure failure, not a
business/Auth error, so it was intentionally left outside the business-error
replacement scope. It is covered by the global `INTERNAL_ERROR` response.

## Second-Pass Safety Corrections

- Added `timestamp` and `path` to every `ErrorResponse`, including MVC,
  Security-entry-point, access-denied, and invalid-JWT responses.
- Prevented login account enumeration: unknown email and wrong password now
  return the same 401 `BAD_CREDENTIALS` response and safe message.
- Prevented resend-OTP account enumeration: unknown emails return the existing
  200 resend response without creating an OTP or sending email.
- Confirmed forgot-password already uses the same generic 200 response for
  existing and unknown emails without creating an OTP or sending email for an
  unknown address.
- Changed `DATA_INTEGRITY_VIOLATION` from 400 to 409 and removed all parsing
  of database exception messages, constraint names, columns, and SQL details.
- Added `ErrorResponseWriter` so Security handlers and the JWT filter use the
  same serialized response instead of duplicating JSON construction.
- Fixed JWT filter response termination and exception scope: every invalid-JWT
  response returns immediately, while only `JwtException` and invalid JWT input
  are mapped to 401.

## Swagger / OpenAPI Error Documentation

All handled Auth errors use `ErrorResponse` as their documented schema. Named
examples in the existing controller annotations include `timestamp`, `status`,
`code`, `message`, and `path`; only validation examples include `errors`.

| Endpoint | Scenario | HTTP status | Error code | Notes |
| --- | --- | --- | --- | --- |
| `POST /auth/register` | Validation failure | 400 | `VALIDATION_ERROR` | Includes field errors. |
| `POST /auth/register` | Malformed JSON | 400 | `MALFORMED_REQUEST` | Safe generic parse message. |
| `POST /auth/register` | Password mismatch | 400 | `PASSWORD_MISMATCH` | Service-level confirmation check. |
| `POST /auth/register` | Existing email | 409 | `DUPLICATE_EMAIL` | Explicit registration conflict. |
| `POST /auth/register` | Persistence conflict | 409 | `DATA_INTEGRITY_VIOLATION` | Safe generic database message. |
| `POST /auth/register` | Unexpected failure | 500 | `INTERNAL_ERROR` | No internal details returned. |
| `POST /auth/login` | Validation or malformed request | 400 | `VALIDATION_ERROR` / `MALFORMED_REQUEST` | Validation includes field errors. |
| `POST /auth/login` | Unknown email or wrong password | 401 | `BAD_CREDENTIALS` | Same response prevents account enumeration. |
| `POST /auth/login` | Email not verified | 403 | `EMAIL_NOT_VERIFIED` | Valid credentials, account not eligible to log in. |
| `POST /auth/login` | Unexpected failure | 500 | `INTERNAL_ERROR` | No internal details returned. |
| `POST /auth/verify-otp` | Validation, malformed request, or invalid OTP | 400 | `VALIDATION_ERROR` / `MALFORMED_REQUEST` / `INVALID_OTP` | Invalid OTP remains a business error. |
| `POST /auth/verify-otp` | User not found | 404 | `USER_NOT_FOUND` | Still returned by the service. |
| `POST /auth/verify-otp` | Expired OTP | 410 | `OTP_EXPIRED` | OTP state is expired. |
| `POST /auth/verify-otp` | Unexpected failure | 500 | `INTERNAL_ERROR` | No internal details returned. |
| `POST /auth/resend-otp` | Existing or unknown email | 200 | - | Same response; unknown email produces no OTP or email. |
| `POST /auth/resend-otp` | Validation or malformed request | 400 | `VALIDATION_ERROR` / `MALFORMED_REQUEST` | Invalid request parameters are normalized. |
| `POST /auth/resend-otp` | Existing-user cooldown | 429 | `OTP_RESEND_COOLDOWN` | Cooldown does not apply to an unknown email. |
| `POST /auth/resend-otp` | Unexpected failure | 500 | `INTERNAL_ERROR` | No internal details returned. |
| `POST /auth/refresh-token` | Malformed request | 400 | `MALFORMED_REQUEST` | Invalid JSON request body. |
| `POST /auth/refresh-token` | Invalid or expired refresh token | 401 | `INVALID_REFRESH_TOKEN` | Also covers a token whose user no longer exists. |
| `POST /auth/refresh-token` | Unexpected failure | 500 | `INTERNAL_ERROR` | No internal details returned. |
| `POST /auth/logout` | Missing or invalid authentication | 401 | `UNAUTHORIZED` | Security handler and invalid JWT use this code. |
| `POST /auth/logout` | Authenticated user not found | 404 | `USER_NOT_FOUND` | Still returned by the service. |
| `POST /auth/logout` | Unexpected failure | 500 | `INTERNAL_ERROR` | No internal details returned. |
| `PATCH /auth/select-role` | Validation, malformed request, or invalid role | 400 | `VALIDATION_ERROR` / `MALFORMED_REQUEST` / `INVALID_ROLE` | Validation and service checks both remain documented. |
| `PATCH /auth/select-role` | Missing or invalid authentication | 401 | `UNAUTHORIZED` | Security handler or invalid principal. |
| `PATCH /auth/select-role` | Reserved role selection | 403 | `ROLE_NOT_ALLOWED` | Service rejects protected roles. |
| `PATCH /auth/select-role` | User not found | 404 | `USER_NOT_FOUND` | Still returned by the service. |
| `PATCH /auth/select-role` | Role already selected | 409 | `ROLE_ALREADY_ASSIGNED` | State conflict. |
| `PATCH /auth/select-role` | Unexpected failure | 500 | `INTERNAL_ERROR` | No internal details returned. |
| `POST /forget-password` | Existing or unknown email | 200 | - | Same generic success response; no OTP/email for unknown email. |
| `POST /forget-password` | Validation or malformed request | 400 | `VALIDATION_ERROR` / `MALFORMED_REQUEST` | Validation includes field errors. |
| `POST /forget-password` | Unexpected failure | 500 | `INTERNAL_ERROR` | No internal details returned. |
| `POST /forget-password/verify-otp` | Validation, malformed request, or invalid OTP | 400 | `VALIDATION_ERROR` / `MALFORMED_REQUEST` / `INVALID_OTP` | Unknown email is intentionally indistinguishable from invalid OTP. |
| `POST /forget-password/verify-otp` | Expired OTP | 410 | `OTP_EXPIRED` | OTP state is expired. |
| `POST /forget-password/verify-otp` | Unexpected failure | 500 | `INTERNAL_ERROR` | No internal details returned. |
| `POST /forget-password/reset` | Validation, malformed request, password mismatch, or invalid reset token | 400 | `VALIDATION_ERROR` / `MALFORMED_REQUEST` / `PASSWORD_MISMATCH` / `INVALID_RESET_TOKEN` | Token and password errors are explicit business cases. |
| `POST /forget-password/reset` | Reset token already used | 409 | `RESET_TOKEN_USED` | State conflict. |
| `POST /forget-password/reset` | Reset token expired | 410 | `RESET_TOKEN_EXPIRED` | Token state is expired. |
| `POST /forget-password/reset` | Unexpected failure | 500 | `INTERNAL_ERROR` | No internal details returned. |

## Swagger Error Examples

Validation error:

```json
{"timestamp":"2026-07-24T01:30:00","status":400,"code":"VALIDATION_ERROR","message":"Request validation failed","path":"/auth/register","errors":{"email":"Invalid email format"}}
```

Malformed request:

```json
{"timestamp":"2026-07-24T01:30:00","status":400,"code":"MALFORMED_REQUEST","message":"Malformed JSON request or invalid data type","path":"/auth/login"}
```

Duplicate email:

```json
{"timestamp":"2026-07-24T01:30:00","status":409,"code":"DUPLICATE_EMAIL","message":"Email already exists","path":"/auth/register"}
```

Bad credentials:

```json
{"timestamp":"2026-07-24T01:30:00","status":401,"code":"BAD_CREDENTIALS","message":"Invalid email or password","path":"/auth/login"}
```

Email not verified:

```json
{"timestamp":"2026-07-24T01:30:00","status":403,"code":"EMAIL_NOT_VERIFIED","message":"Email not verified. Please verify your email first","path":"/auth/login"}
```

Invalid OTP:

```json
{"timestamp":"2026-07-24T01:30:00","status":400,"code":"INVALID_OTP","message":"Invalid OTP","path":"/auth/verify-otp"}
```

Expired OTP:

```json
{"timestamp":"2026-07-24T01:30:00","status":410,"code":"OTP_EXPIRED","message":"OTP has expired","path":"/auth/verify-otp"}
```

OTP cooldown:

```json
{"timestamp":"2026-07-24T01:30:00","status":429,"code":"OTP_RESEND_COOLDOWN","message":"Please wait 2 minutes before requesting a new OTP","path":"/auth/resend-otp"}
```

Invalid refresh token:

```json
{"timestamp":"2026-07-24T01:30:00","status":401,"code":"INVALID_REFRESH_TOKEN","message":"Expired token, please login again","path":"/auth/refresh-token"}
```

Unauthorized:

```json
{"timestamp":"2026-07-24T01:30:00","status":401,"code":"UNAUTHORIZED","message":"Authentication is required.","path":"/auth/logout"}
```

Forbidden:

```json
{"timestamp":"2026-07-24T01:30:00","status":403,"code":"FORBIDDEN","message":"Access is denied.","path":"/auth/select-role"}
```

Database conflict:

```json
{"timestamp":"2026-07-24T01:30:00","status":409,"code":"DATA_INTEGRITY_VIOLATION","message":"A data conflict occurred.","path":"/auth/register"}
```

Internal error:

```json
{"timestamp":"2026-07-24T01:30:00","status":500,"code":"INTERNAL_ERROR","message":"An unexpected error occurred","path":"/auth/login"}
```

## Mobile Swagger Additions

- The OpenAPI description now explains the complete mobile Auth flow, Bearer
  header format, secure token storage, refresh-token behavior, and the correct
  reaction to `INVALID_REFRESH_TOKEN`.
- `ErrorResponse` properties have Swagger descriptions so mobile developers
  can treat `code` as the stable programmatic contract and `message` as display
  text.
- Login and email-OTP verification document secure storage of returned tokens.
- Password-reset documentation explains that request success is intentionally
  generic and that the reset token is a short-lived secret.
- OTP documentation includes the 10-minute registration OTP lifetime, the
  5-minute password-reset OTP lifetime, and the 2-minute resend cooldown.
- Register and login explicitly document their stricter rate limit. The actual
  429 response shape and `Retry-After` header are documented globally for all
  endpoints because `RateLimitingFilter` applies a default limit elsewhere.

## Verification

Compile passed:

```powershell
.\mvnw.cmd -q -DskipTests compile
```

Result: exit code 0.

The existing test suite was run without modifications:

```powershell
.\mvnw.cmd test
```

Result: exit code 1. The test application context could not connect to the
configured PostgreSQL instance because authentication for user `postgres`
failed. The failure happened before the test suite could complete; no test,
database, or configuration file was changed to work around it.

OpenAPI generation and Swagger UI could not be verified live because the same
database authentication failure prevents the Spring application context from
starting. The Auth OpenAPI annotations compiled successfully, and a static
check confirmed that the Auth controllers no longer contain legacy
`errorMessages` examples.

No existing tests were modified and no new tests were added.
