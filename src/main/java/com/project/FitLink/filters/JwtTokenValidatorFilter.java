package com.project.FitLink.filters;

import com.project.FitLink.repository.users.UserRepository;
import com.project.FitLink.service.jwtService;
import com.project.FitLink.utils.Constants;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenValidatorFilter extends OncePerRequestFilter {

    private final jwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(Constants.JWT_HEADER);
        if (token == null || token.isBlank() || !token.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String accessToken = token.substring(7);
        try {
            Claims claims = jwtService.extractClaims(accessToken);
            Long userId = claims.get("id", Long.class);
            Integer tokenVersion = claims.get("tokenVersion", Integer.class);

            int currentVersion = userRepository.findById(userId)
                    .map(u -> u.getTokenVersion())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (tokenVersion == null || tokenVersion != currentVersion) {
                log.warn("Token version mismatch for user: {}", userId);
                throw new RuntimeException("Token has been invalidated");
            }

            jwtService.validateTokenForFilter(accessToken);
            log.debug("Token validated successfully for user: {}", userId);
        } catch (Exception ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid or expired JWT token\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}

