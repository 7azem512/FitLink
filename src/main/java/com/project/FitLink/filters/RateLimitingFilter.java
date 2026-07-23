package com.project.FitLink.filters;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int REQUESTS_PER_MINUTE = 60;
    private static final int REQUESTS_PER_HOUR_LOGIN = 10;
    private static final double DEFAULT_RATE = REQUESTS_PER_MINUTE / 60.0;
    private static final double LOGIN_RATE = REQUESTS_PER_HOUR_LOGIN / 3600.0;

    private final LoadingCache<String, RateLimiter> limiters = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build(new CacheLoader<String, RateLimiter>() {
                @Override
                public RateLimiter load(String key) {
                    return RateLimiter.create(DEFAULT_RATE);
                }
            });

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        String clientIP = getClientIp(request);
        String path = request.getRequestURI();
        String key = clientIP + ":" + path;

        try {
            RateLimiter rateLimiter = limiters.get(key);
            
            if (path.contains("/auth/login") || path.contains("/auth/register")) {
                rateLimiter.setRate(LOGIN_RATE);
            } else {
                rateLimiter.setRate(DEFAULT_RATE);
            }

            if (rateLimiter.tryAcquire()) {
                filterChain.doFilter(request, response);
            } else {
                log.warn("Rate limit exceeded for IP: {} on path: {}", clientIP, path);
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Rate limit exceeded. Please try again later.\"}");
            }
        } catch (ExecutionException e) {
            log.error("Error in rate limiting filter: {}", e.getMessage());
            filterChain.doFilter(request, response);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp != null && !clientIp.isEmpty()) {
            return clientIp.split(",")[0].trim();
        }
        clientIp = request.getHeader("X-Real-IP");
        if (clientIp != null && !clientIp.isEmpty()) {
            return clientIp;
        }
        return request.getRemoteAddr();
    }
}

