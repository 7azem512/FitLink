package com.project.FitLink.service;

import com.project.FitLink.auth.FitLinkUserDetails;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.entities.users.UserRole;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class jwtService {
    private long accessTokenExpiration = Constants.JWT_ACCESS_TOKEN_EXPIRATION;
    private long refreshTokenExpiration = Constants.JWT_REFRESH_TOKEN_EXPIRATION;

    @Value("${application.jwt.secret}")
    private String JWT_SECRET_DEFAULT_VALUE;

    private SecretKey secretKey;

    /**
     * Generates an access token for the current user from SecurityContext.
     * @return The generated access token.
     */
    public String generateAccessToken(){
        FitLinkUserDetails user = getCurrentUser();
        return generateAccessToken(user.getId(), user.getEmail(), user.getUsername(), user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(",")), user.getTokenVersion());
    }

    /**
     * Generates an access token for a given UserEntity.
     * @param userEntity The UserEntity to generate the token for.
     * @return The generated access token.
     */
    public String generateAccessToken(UserEntity userEntity){
        String authorities = userEntity.getRoles().stream()
                .map(userRole -> userRole.getRoleCode().name())
                .collect(Collectors.joining(","));
        return generateAccessToken(userEntity.getId(), userEntity.getEmail(), userEntity.getUserName(), authorities, userEntity.getTokenVersion());
    }

    private String generateAccessToken(Long id, String email, String username, String authorities, int tokenVersion){
        SecretKey key = getSecretKey();
        return Jwts.builder()
                .issuer("FitLink")
                .subject("ACCESS Token")
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + accessTokenExpiration))
                .claim("id", id)
                .claim("email", email)
                .claim("userName", username)
                .claim("authorities", authorities)
                .claim("tokenVersion", tokenVersion)
                .signWith(key)
                .compact();
    }

    /**
     * Generates a refresh token for the current user from SecurityContext.
     * @return The generated refresh token.
     */
    public String generateRefreshToken(){
        FitLinkUserDetails user = getCurrentUser();
        return generateRefreshToken(user.getId(), user.getEmail(), user.getUsername(), user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(",")), user.getTokenVersion());
    }

    /**
     * Generates a refresh token for a given UserEntity.
     * @param userEntity The UserEntity to generate the token for.
     * @return The generated refresh token.
     */
    public String generateRefreshToken(UserEntity userEntity){
        String authorities = userEntity.getRoles().stream()
                .map(userRole -> userRole.getRoleCode().name())
                .collect(Collectors.joining(","));
        return generateRefreshToken(userEntity.getId(), userEntity.getEmail(), userEntity.getUserName(), authorities, userEntity.getTokenVersion());
    }

    private String generateRefreshToken(Long id, String email, String username, String authorities, int tokenVersion){
        SecretKey key = getSecretKey();
        return Jwts.builder()
                .issuer("FitLink")
                .subject("REFRESH Token")
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + refreshTokenExpiration))
                .claim("id", id)
                .claim("email", email)
                .claim("userName", username)
                .claim("authorities", authorities)
                .claim("tokenVersion", tokenVersion)
                .signWith(key)
                .compact();
    }

    /**
     * Validates a token and sets the authentication in the security context.
     * @param token The token to validate.
     */
    public void validateTokenForFilter(String token) {
        SecretKey key = getSecretKey();

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long id = claims.get("id", Long.class);
        String email = claims.get("email", String.class);
        String username = claims.get("userName", String.class);
        String authorities = claims.get("authorities", String.class);
        Integer tokenVersion = claims.get("tokenVersion", Integer.class);


        FitLinkUserDetails userDetails = FitLinkUserDetails.builder()
                .id(id)
                .username(username)
                .email(email)
                .password(null)
                .authorities(
                        AuthorityUtils.commaSeparatedStringToAuthorityList(authorities)
                )
                .tokenVersion(tokenVersion != null ? tokenVersion : 0) // Default to 0 if not present
                .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Checks if the token is valid.
     * @param Token The token to validate.
     * @return true if the token is valid, false otherwise.
     */
    public boolean isTokenValid(String Token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(Token)
                    .getPayload();

            Date expiration = claims.getExpiration();

            return expiration.after(new Date());

        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts the claims from the token.
     * @param token The token to extract claims from.
     * @return The claims extracted from the token.
     */
    public Claims extractClaims(String token){
        SecretKey key = getSecretKey();
        return Jwts.parser().verifyWith(key)
                .build().parseSignedClaims(token).getPayload();
    }

    /**
     * Retrieves the secret key for JWT operations.
     * @return The secret key.
     */
    public SecretKey getSecretKey() {
        if (secretKey == null) {
            String secret = JWT_SECRET_DEFAULT_VALUE;
            secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        return secretKey;
    }

    /**
     * Retrieves the current user details from the security context.
     * @return The current user details.
     */
    private FitLinkUserDetails getCurrentUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !(authentication.getPrincipal() instanceof FitLinkUserDetails userDetails)) {
            throw new UsernameNotFoundException("No authenticated user found");
        }

        return userDetails;
    }

}
