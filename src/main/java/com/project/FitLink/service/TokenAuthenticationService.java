package com.project.FitLink.service;

import com.project.FitLink.auth.FitLinkUserDetails;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.repository.users.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenAuthenticationService {

    private static final String ACCESS_TOKEN_SUBJECT = "ACCESS Token";

    private final jwtService jwtService;
    private final UserRepository userRepository;

    public Authentication authenticate(String token) {
        Claims claims = jwtService.extractClaims(token);

        if (!ACCESS_TOKEN_SUBJECT.equals(claims.getSubject())) {
            throw new JwtException("Invalid token type");
        }

        UUID publicId = UUID.fromString(claims.getId());
        Integer tokenVersion = claims.get("tokenVersion", Integer.class);

        if (tokenVersion == null) {
            throw new JwtException("Missing tokenVersion");
        }

        UserEntity user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new JwtException("User not found"));

        if (!Objects.equals(tokenVersion, user.getTokenVersion())) {
            throw new JwtException("Token version mismatch");
        }

        String email    = claims.get("email", String.class);
        String userName = claims.get("userName", String.class);
        String authStr  = claims.get("authorities", String.class);

        Collection<SimpleGrantedAuthority> authorities =
                authStr == null || authStr.isBlank()
                        ? List.of()
                        : Arrays.stream(authStr.split(","))
                                .map(String::trim)
                                .filter(a -> !a.isBlank())
                                .map(SimpleGrantedAuthority::new)
                                .toList();

        FitLinkUserDetails userDetails = FitLinkUserDetails.builder()
                .id(user.getId())
                .publicId(user.getPublicId())
                .username(userName)
                .email(email)
                .password(null)
                .tokenVersion(user.getTokenVersion())
                .authorities(authorities)
                .build();

        log.debug("JWT authenticated for publicId={}", publicId);
        return new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
    }
}
