package com.project.FitLink.filters;

import com.project.FitLink.auth.FitLinkUserDetails;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.exception.ErrorCode;
import com.project.FitLink.exception.ErrorResponseWriter;
import com.project.FitLink.repository.users.UserRepository;
import com.project.FitLink.service.jwtService;
import com.project.FitLink.utils.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
            String email = claims.get("email", String.class);
            String userName = claims.get("userName", String.class);
            String authorities = claims.get("authorities", String.class);

            if (userId == null || tokenVersion == null) {
                rejectInvalidJwt(request, response);
                return;
            }

            UserEntity userEntity = userRepository.findById(userId).orElse(null);
            if (userEntity == null) {
                rejectInvalidJwt(request, response);
                return;
            }
            
            int currentVersion = userEntity.getTokenVersion();

            if (tokenVersion == null || tokenVersion != currentVersion) {
                log.warn("JWT token version mismatch");
                rejectInvalidJwt(request, response);
                return;
            }

            jwtService.validateTokenForFilter(accessToken);
            
            // Build authorities collection
            Collection<SimpleGrantedAuthority> grantedAuthorities = new ArrayList<>();
            if (authorities != null && !authorities.isEmpty()) {
                grantedAuthorities.add(new SimpleGrantedAuthority(authorities));
            }
            
            // Create FitLinkUserDetails with proper data
            FitLinkUserDetails userDetails = FitLinkUserDetails.builder()
                    .id(userId)
                    .username(userName)
                    .email(email)
                    .password(userEntity.getPassword())
                    .tokenVersion(currentVersion)
                    .authorities(grantedAuthorities)
                    .build();
            
            // Create Authentication object and set it in SecurityContext
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    grantedAuthorities
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            log.debug("Token validated successfully for user: {}", userId);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT validation failed for path={}", request.getRequestURI());
            rejectInvalidJwt(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void rejectInvalidJwt(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        ErrorResponseWriter.write(
                response,
                ErrorCode.UNAUTHORIZED,
                "Invalid or expired JWT token.",
                request.getRequestURI()
        );
    }
}

