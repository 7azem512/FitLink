package com.project.FitLink.exception.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.FitLink.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ErrorResponseWriter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ErrorResponseWriter() {
    }

    public static void write(
            HttpServletResponse response,
            ErrorCode errorCode,
            String message,
            String path
    ) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                OBJECT_MAPPER.writeValueAsString(ErrorResponse.of(errorCode, message, path))
        );
    }
}
