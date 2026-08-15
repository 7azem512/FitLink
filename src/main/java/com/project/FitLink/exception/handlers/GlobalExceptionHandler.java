package com.project.FitLink.exception.handlers;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.project.FitLink.exception.AppException;
import com.project.FitLink.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler{

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, message, request.getRequestURI()));
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            ErrorCode errorCode,
            String message,
            Map<String, String> errors,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, message, request.getRequestURI(), errors));
    }

    private void logExpected(ErrorCode errorCode, HttpServletRequest request) {
        log.warn("Handled error code={} path={}", errorCode, request.getRequestURI());
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(
            AppException exception,
            HttpServletRequest request
    ) {
        logExpected(exception.getErrorCode(), request);
        return buildErrorResponse(exception.getErrorCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();

        for(FieldError fieldError : fieldErrors){
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        exception.getBindingResult().getGlobalErrors().forEach(error -> {
            errors.put(error.getObjectName(), error.getDefaultMessage());
        });

        logExpected(ErrorCode.VALIDATION_ERROR, request);
        return buildErrorResponse(ErrorCode.VALIDATION_ERROR, "Request validation failed", errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonParseException(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (ex.getCause() instanceof InvalidFormatException) {
            InvalidFormatException ifx = (InvalidFormatException) ex.getCause();

            if (ifx.getTargetType() != null && ifx.getTargetType().isEnum()) {
                String fieldName = ifx.getPath().get(ifx.getPath().size() - 1).getFieldName();
                String rejectedValue = String.valueOf(ifx.getValue());
                String allowedValues = Arrays.toString(ifx.getTargetType().getEnumConstants());

                String errorMessage;
                if (rejectedValue.isEmpty()) {
                    errorMessage = "Value cannot be empty. Accepted values are: " + allowedValues;
                } else {
                    errorMessage = String.format("Invalid value '%s'. Accepted values are: %s", rejectedValue, allowedValues);
                }
                errors.put(fieldName, errorMessage);
                logExpected(ErrorCode.MALFORMED_REQUEST, request);
                return buildErrorResponse(
                        ErrorCode.MALFORMED_REQUEST,
                        "Malformed JSON request or invalid data type",
                        errors,
                        request
                );
            }
        }

        logExpected(ErrorCode.MALFORMED_REQUEST, request);
        return buildErrorResponse(ErrorCode.MALFORMED_REQUEST, "Malformed JSON request or invalid data type", request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex,
            HttpServletRequest request
    ) {
        logExpected(ErrorCode.NOT_FOUND, request);
        return buildErrorResponse(ErrorCode.NOT_FOUND, "Entity not found", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        logExpected(ErrorCode.DATA_INTEGRITY_VIOLATION, request);
        return buildErrorResponse(
                ErrorCode.DATA_INTEGRITY_VIOLATION,
                "A data conflict occurred.",
                request
        );
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUserName(
            UsernameNotFoundException ex,
            HttpServletRequest request
    ) {
        logExpected(ErrorCode.USER_NOT_FOUND, request);
        return buildErrorResponse(ErrorCode.USER_NOT_FOUND, "User not found", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request
    ) {
        logExpected(ErrorCode.BAD_CREDENTIALS, request);
        return buildErrorResponse(ErrorCode.BAD_CREDENTIALS, "Invalid email or password", request);
    }

    @ExceptionHandler(UnrecognizedPropertyException.class)
    public ResponseEntity<ErrorResponse> handleUnknownField(
            UnrecognizedPropertyException ex,
            HttpServletRequest request
    ) {
        logExpected(ErrorCode.MALFORMED_REQUEST, request);
        return buildErrorResponse(
                ErrorCode.MALFORMED_REQUEST,
                "Field '" + ex.getPropertyName() + "' is not allowed",
                Map.of(ex.getPropertyName(), "Field is not allowed"),
                request
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.put(violation.getPropertyPath().toString(), violation.getMessage())
        );

        logExpected(ErrorCode.VALIDATION_ERROR, request);
        return buildErrorResponse(ErrorCode.VALIDATION_ERROR, "Request validation failed", errors, request);
    }

    // Thrown by the servlet container when a multipart request exceeds the configured limits.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request
    ) {
        logExpected(ErrorCode.FILE_TOO_LARGE, request);
        return buildErrorResponse(ErrorCode.FILE_TOO_LARGE, "The uploaded file exceeds the maximum allowed size", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        logExpected(ErrorCode.MALFORMED_REQUEST, request);
        return buildErrorResponse(
                ErrorCode.MALFORMED_REQUEST,
                "Required request parameter is missing",
                Map.of(ex.getParameterName(), "Required parameter is missing"),
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        logExpected(ErrorCode.MALFORMED_REQUEST, request);
        return buildErrorResponse(
                ErrorCode.MALFORMED_REQUEST,
                "Request parameter has an invalid value",
                Map.of(ex.getName(), "Invalid value"),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unexpected error while handling path={}", request.getRequestURI(), exception);
        return buildErrorResponse(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request);
    }
}
