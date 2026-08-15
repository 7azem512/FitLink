package com.project.FitLink.filters;

import com.project.FitLink.exception.ErrorCode;
import com.project.FitLink.exception.handlers.ErrorResponseWriter;
import com.project.FitLink.service.auth.TokenAuthenticationService;
import com.project.FitLink.utils.Constants;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenValidatorFilter extends OncePerRequestFilter {

    private final TokenAuthenticationService tokenAuthenticationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Authentication auth = tokenAuthenticationService.authenticate(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT validation failed for path={}: {}", sanitize(request.getRequestURI()), ex.getMessage(), ex);
            SecurityContextHolder.clearContext();
            ErrorResponseWriter.write(response, ErrorCode.UNAUTHORIZED, "Invalid or expired JWT token.", request.getRequestURI());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(Constants.JWT_HEADER);
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7).trim();
        return token.isBlank() ? null : token;
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replace("\r", "_").replace("\n", "_");
    }
}
