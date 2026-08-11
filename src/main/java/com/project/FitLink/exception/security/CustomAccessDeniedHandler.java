package com.project.FitLink.exception.security;

import com.project.FitLink.exception.ErrorCode;
import com.project.FitLink.exception.handlers.ErrorResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setHeader("FitLink-denied-reason", "Authorization failed");
        ErrorResponseWriter.write(
                response,
                ErrorCode.FORBIDDEN,
                "Access is denied.",
                request.getRequestURI()
        );
    }
}
