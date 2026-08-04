package com.project.FitLink.service;

import com.project.FitLink.auth.FitLinkUserDetails;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.utils.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class jwtService {
    private final long accessTokenExpiration = Constants.JWT_ACCESS_TOKEN_EXPIRATION;
    private final long refreshTokenExpiration = Constants.JWT_REFRESH_TOKEN_EXPIRATION;

    @Value("${application.jwt.secret}")
    private String JWT_SECRET_DEFAULT_VALUE;

    private SecretKey secretKey;

    /** Generates an access token for the current user from SecurityContext. */
    public String generateAccessToken() {
        FitLinkUserDetails user = getCurrentUser();
        return buildAccessToken(
                user.getPublicId(),
                user.getEmail(),
                user.getUsername(),
                user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(",")),
                user.getTokenVersion()
        );
    }

    /** Generates an access token for a given UserEntity. */
    public String generateAccessToken(UserEntity userEntity) {
        String authorities = userEntity.getRoles().stream()
                .map(userRole -> "ROLE_" + userRole.getRole().getRoleCode().name())
                .collect(Collectors.joining(","));
        return buildAccessToken(userEntity.getPublicId(), userEntity.getEmail(), userEntity.getUserName(), authorities, userEntity.getTokenVersion());
    }

    private String buildAccessToken(UUID publicId, String email, String username, String authorities, int tokenVersion) {
        return Jwts.builder()
                .issuer("FitLink")
                .subject("ACCESS Token")
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + accessTokenExpiration))
                .claim("id", publicId)
                .claim("email", email)
                .claim("userName", username)
                .claim("authorities", authorities)
                .claim("tokenVersion", tokenVersion)
                .signWith(getSecretKey())
                .compact();
    }

    /** Generates a refresh token for the current user from SecurityContext. */
    public String generateRefreshToken() {
        FitLinkUserDetails user = getCurrentUser();
        return buildRefreshToken(
                user.getPublicId(),
                user.getEmail(),
                user.getUsername(),
                user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(",")),
                user.getTokenVersion()
        );
    }

    /** Generates a refresh token for a given UserEntity. */
    public String generateRefreshToken(UserEntity userEntity) {
        String authorities = userEntity.getRoles().stream()
                .map(userRole -> "ROLE_" + userRole.getRole().getRoleCode().name())
                .collect(Collectors.joining(","));
        return buildRefreshToken(userEntity.getPublicId(), userEntity.getEmail(), userEntity.getUserName(), authorities, userEntity.getTokenVersion());
    }

    private String buildRefreshToken(UUID publicId, String email, String username, String authorities, int tokenVersion) {
        return Jwts.builder()
                .issuer("FitLink")
                .subject("REFRESH Token")
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + refreshTokenExpiration))
                .claim("id", publicId)
                .claim("email", email)
                .claim("userName", username)
                .claim("authorities", authorities)
                .claim("tokenVersion", tokenVersion)
                .signWith(getSecretKey())
                .compact();
    }

    /** Checks if the token is valid (signature + expiry). */
    public boolean isTokenValid(String token) {
        try {
            Date expiration = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getExpiration();
            return expiration.after(new Date());
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /** Extracts the claims from the token. */
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public SecretKey getSecretKey() {
        if (secretKey == null) {
            secretKey = Keys.hmacShaKeyFor(JWT_SECRET_DEFAULT_VALUE.getBytes(StandardCharsets.UTF_8));
        }
        return secretKey;
    }

    private FitLinkUserDetails getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null ||
                !(authentication.getPrincipal() instanceof FitLinkUserDetails userDetails)) {
            throw new UsernameNotFoundException("No authenticated user found");
        }
        return userDetails;
    }
}
