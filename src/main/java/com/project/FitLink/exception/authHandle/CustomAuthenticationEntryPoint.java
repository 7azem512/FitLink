package com.project.FitLink.exception.authHandle;

import com.project.FitLink.exception.ErrorCode;
import com.project.FitLink.exception.ErrorResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        response.setHeader("error-reason", "Authentication failed");
        ErrorResponseWriter.write(
                response,
                ErrorCode.UNAUTHORIZED,
                "Authentication is required.",
                request.getRequestURI()
        );
    }
}
