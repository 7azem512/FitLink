package com.project.FitLink.filters;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    /*
     * Development limits
     *
     * Default endpoints:
     * 1000 requests per minute for each IP.
     */
    private static final int DEFAULT_LIMIT = 1000;

    private static final long DEFAULT_WINDOW_NANOS =
            TimeUnit.MINUTES.toNanos(1);

    /*
     * Login and register:
     * 200 requests per hour for each IP.
     */
//    private static final int DEFAULT_LIMIT = 60;
//    private static final int AUTH_LIMIT = 10;
    private static final int AUTH_LIMIT = 200;

    private static final long AUTH_WINDOW_NANOS =
            TimeUnit.HOURS.toNanos(1);

    private static final int MAX_CACHE_SIZE = 100_000;

    private static final String LOGIN_PATH = "/auth/login";
    private static final String REGISTER_PATH = "/auth/register";

    private final Cache<String, RequestWindow> requestWindows =
            CacheBuilder.newBuilder()
                    .maximumSize(MAX_CACHE_SIZE)
                    .expireAfterAccess(2, TimeUnit.HOURS)
                    .build();

    @Override
    protected boolean shouldNotFilter(
            @NonNull HttpServletRequest request
    ) {

        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String clientIp = getClientIp(request);
        String path = request.getServletPath();

        RateLimitPolicy policy = resolvePolicy(path);

        String cacheKey = buildCacheKey(
                clientIp,
                policy.bucketName()
        );

        RequestWindow requestWindow = requestWindows.asMap()
                .computeIfAbsent(
                        cacheKey,
                        key -> new RequestWindow()
                );

        RateLimitResult result = requestWindow.tryConsume(
                policy.limit(),
                policy.windowNanos()
        );

        if (result.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = calculateRetryAfterSeconds(
                result.retryAfterNanos()
        );

        log.warn(
                "Rate limit exceeded. ip={}, path={}, retryAfter={}s",
                clientIp,
                path,
                retryAfterSeconds
        );

        sendRateLimitResponse(
                response,
                retryAfterSeconds
        );
    }

    private RateLimitPolicy resolvePolicy(String path) {

        if (LOGIN_PATH.equals(path)) {
            return new RateLimitPolicy(
                    "LOGIN",
                    AUTH_LIMIT,
                    AUTH_WINDOW_NANOS
            );
        }

        if (REGISTER_PATH.equals(path)) {
            return new RateLimitPolicy(
                    "REGISTER",
                    AUTH_LIMIT,
                    AUTH_WINDOW_NANOS
            );
        }

        return new RateLimitPolicy(
                "DEFAULT",
                DEFAULT_LIMIT,
                DEFAULT_WINDOW_NANOS
        );
    }

    private String buildCacheKey(
            String clientIp,
            String bucketName
    ) {
        return clientIp + ":" + bucketName;
    }

    private String getClientIp(
            HttpServletRequest request
    ) {

        /*
         * Use getRemoteAddr during development.
         *
         * Do not trust X-Forwarded-For until the application
         * is behind a correctly configured trusted proxy.
         */
        return request.getRemoteAddr();
    }

    private long calculateRetryAfterSeconds(
            long retryAfterNanos
    ) {

        return Math.max(
                1,
                TimeUnit.NANOSECONDS.toSeconds(
                        retryAfterNanos
                ) + 1
        );
    }

    private void sendRateLimitResponse(
            HttpServletResponse response,
            long retryAfterSeconds
    ) throws IOException {

        response.setStatus(
                HttpStatus.TOO_MANY_REQUESTS.value()
        );

        response.setHeader(
                "Retry-After",
                String.valueOf(retryAfterSeconds)
        );

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );

        response.getWriter().write(
                """
                {
                  "status": 429,
                  "error": "Too Many Requests",
                  "message": "Too many requests. Please try again later."
                }
                """
        );
    }

    private record RateLimitPolicy(
            String bucketName,
            int limit,
            long windowNanos
    ) {
    }

    private record RateLimitResult(
            boolean allowed,
            long retryAfterNanos
    ) {

        private static RateLimitResult allowedResult() {
            return new RateLimitResult(
                    true,
                    0
            );
        }

        private static RateLimitResult rejectedResult(
                long retryAfterNanos
        ) {
            return new RateLimitResult(
                    false,
                    retryAfterNanos
            );
        }
    }

    private static class RequestWindow {

        private long windowStartedAtNanos =
                System.nanoTime();

        private int requestCount = 0;

        public synchronized RateLimitResult tryConsume(
                int limit,
                long windowNanos
        ) {

            long now = System.nanoTime();

            long elapsedNanos =
                    now - windowStartedAtNanos;

            if (elapsedNanos >= windowNanos) {
                resetWindow(now);
                elapsedNanos = 0;
            }

            if (requestCount < limit) {
                requestCount++;

                return RateLimitResult.allowedResult();
            }

            long retryAfterNanos =
                    windowNanos - elapsedNanos;

            return RateLimitResult.rejectedResult(
                    retryAfterNanos
            );
        }

        private void resetWindow(long now) {
            windowStartedAtNanos = now;
            requestCount = 0;
        }
    }
}