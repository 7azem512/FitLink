package com.project.FitLink.exception.handlers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.project.FitLink.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse {
    @Schema(description = "ISO-8601 server timestamp when the error response was created", example = "2026-07-24T01:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String timestamp;

    @Schema(description = "HTTP response status", example = "400", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int status;

    @Schema(description = "Stable machine-readable application error code. Mobile clients should use this value for behavior and display message as user-facing text.", example = "VALIDATION_ERROR", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String code;

    @Schema(description = "Safe client-facing description of the error", example = "Request validation failed", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String message;

    @Schema(description = "Request URI that produced the error", example = "/auth/register", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String path;

    @Schema(description = "Field-level messages returned only for validation errors", example = "{\"email\": \"Invalid email format\"}")
    private final Map<String, String> errors;

    private ErrorResponse(
            String timestamp,
            int status,
            String code,
            String message,
            String path,
            Map<String, String> errors
    ) {
        this.timestamp = timestamp;
        this.status = status;
        this.code = code;
        this.message = message;
        this.path = path;
        this.errors = errors;
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return of(errorCode, message, path, Collections.emptyMap());
    }

    public static ErrorResponse of(
            ErrorCode errorCode,
            String message,
            String path,
            Map<String, String> errors
    ) {
        return new ErrorResponse(
                LocalDateTime.now().toString(),
                errorCode.getStatus().value(),
                errorCode.name(),
                message,
                path,
                errors
        );
    }
}
