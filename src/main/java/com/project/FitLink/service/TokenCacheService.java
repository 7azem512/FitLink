package com.project.FitLink.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.project.FitLink.auth.FitLinkUserDetails;
import com.project.FitLink.entities.TokenBlacklist;
import com.project.FitLink.repository.TokenBlacklistRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenCacheService {

    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final Cache<String, Boolean> tokenBlacklistCache = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    private final Cache<String, FitLinkUserDetails> userDetailsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(5000)
            .build();

    @Value("${application.jwt.access-token-expiration:3600000}")
    private long accessTokenExpiration;

    public void addToBlacklist(String token, Long userId, String reason) {
        try {
            Claims claims = extractClaimsUnsafely(token);
            LocalDateTime expiryDate = new java.util.Date(claims.getExpiration().getTime()).toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();

            TokenBlacklist blacklistedToken = TokenBlacklist.builder()
                    .token(token)
                    .userId(userId)
                    .blacklistedAt(LocalDateTime.now())
                    .expiryDate(expiryDate)
                    .reason(reason)
                    .build();

            tokenBlacklistRepository.save(blacklistedToken);
            tokenBlacklistCache.put(token, true);
            log.info("Token added to blacklist for user: {}", userId);
        } catch (Exception e) {
            log.error("Error adding token to blacklist: {}", e.getMessage());
        }
    }

    public boolean isTokenBlacklisted(String token) {
        Boolean cached = tokenBlacklistCache.getIfPresent(token);
        if (cached != null) {
            return cached;
        }

        boolean exists = tokenBlacklistRepository.existsByToken(token);
        tokenBlacklistCache.put(token, exists);
        return exists;
    }

    public void cacheUserDetails(String email, FitLinkUserDetails details) {
        userDetailsCache.put(email, details);
    }

    public Optional<FitLinkUserDetails> getCachedUserDetails(String email) {
        return Optional.ofNullable(userDetailsCache.getIfPresent(email));
    }

    public void invalidateUserCache(String email) {
        userDetailsCache.invalidate(email);
    }

    @Async
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            tokenBlacklistRepository.deleteExpiredTokens(LocalDateTime.now());
            log.info("Expired tokens cleaned up");
        } catch (Exception e) {
            log.error("Error cleaning up expired tokens: {}", e.getMessage());
        }
    }

    private Claims extractClaimsUnsafely(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid token format");
        }
        return null;
    }
}
