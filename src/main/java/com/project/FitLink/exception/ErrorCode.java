package com.project.FitLink.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_OTP(HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(HttpStatus.GONE),
    OTP_RESEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST),
    RESET_TOKEN_EXPIRED(HttpStatus.GONE),
    RESET_TOKEN_USED(HttpStatus.CONFLICT),
    INVALID_ROLE(HttpStatus.BAD_REQUEST),
    ROLE_ALREADY_ASSIGNED(HttpStatus.CONFLICT),
    ROLE_NOT_ALLOWED(HttpStatus.FORBIDDEN),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }
}
