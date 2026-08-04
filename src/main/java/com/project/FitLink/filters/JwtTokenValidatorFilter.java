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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenValidatorFilter extends OncePerRequestFilter {

    private final jwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(Constants.JWT_HEADER);
        if (header == null || header.isBlank() || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String accessToken = header.substring(7);
        try {
            Claims claims = jwtService.extractClaims(accessToken);

            UUID publicId = UUID.fromString(claims.getSubject());
            Integer tokenVersion = claims.get("tokenVersion", Integer.class);
            String email    = claims.get("email", String.class);
            String userName = claims.get("userName", String.class);
            String authorities = claims.get("authorities", String.class);

            if (tokenVersion == null) {
                rejectInvalidJwt(request, response);
                return;
            }

            UserEntity userEntity = userRepository.findByPublicId(publicId).orElse(null);
            if (userEntity == null || tokenVersion != userEntity.getTokenVersion()) {
                log.warn("JWT rejected: publicId={}", publicId);
                rejectInvalidJwt(request, response);
                return;
            }

            Collection<SimpleGrantedAuthority> grantedAuthorities = authorities != null && !authorities.isBlank()
                    ? List.of(new SimpleGrantedAuthority(authorities))
                    : List.of();

            FitLinkUserDetails userDetails = FitLinkUserDetails.builder()
                    .id(userEntity.getId())
                    .publicId(publicId)
                    .username(userName)
                    .email(email)
                    .password(null)
                    .tokenVersion(userEntity.getTokenVersion())
                    .authorities(grantedAuthorities)
                    .build();

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, grantedAuthorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT validated for publicId={}", publicId);

        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT validation failed for path={}: {}", request.getRequestURI(), ex.getMessage());
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
