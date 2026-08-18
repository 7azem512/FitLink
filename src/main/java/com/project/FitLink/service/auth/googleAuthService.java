package com.project.FitLink.service.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.project.FitLink.auth.FitLinkUserDetails;
import com.project.FitLink.auth.GoogleTokenVerifier;
import com.project.FitLink.dto.Auth.login.GoogleLoginRequest;
import com.project.FitLink.dto.Auth.login.TokenResponse;
import com.project.FitLink.entities.users.Role;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.entities.users.UserRole;
import com.project.FitLink.exception.AppException;
import com.project.FitLink.exception.ErrorCode;
import com.project.FitLink.repository.users.RoleRepository;
import com.project.FitLink.repository.users.UserRepository;
import com.project.FitLink.repository.users.UserRoleRepository;
import com.project.FitLink.utils.FitLinkUtils;
import com.project.FitLink.utils.enums.auth.AuthProvider;
import com.project.FitLink.utils.enums.user.Roles;
import com.project.FitLink.utils.enums.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.project.FitLink.utils.enums.auth.AuthProvider.GOOGLE;

/**
 * Signs a user in with a verified Google ID token.
 *
 * <p>The Google identity is taken ONLY from the cryptographically verified ID token.
 * The token is used to authenticate once with Google; afterwards the application's
 * own JWT access/refresh tokens (existing {@link jwtService} implementation) are issued.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class googleAuthService {

    private static final int USERNAME_MAX_LENGTH = 50;
    private static final int USERNAME_MIN_LENGTH = 3;

    private final GoogleTokenVerifier googleTokenVerifier;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final jwtService jwtService;

    @Transactional
    public TokenResponse authenticateWithGoogle(GoogleLoginRequest request) {
        GoogleIdToken.Payload payload = googleTokenVerifier.verifyAndExtractPayload(request.getIdToken());

        String providerId = payload.getSubject();
        String email = payload.getEmail();

        UserEntity user = userRepository.findByProviderAndProviderId(GOOGLE, providerId)
                .orElseGet(() -> findOrCreateGoogleUser(payload, providerId, email));

        List<SimpleGrantedAuthority> authorities = FitLinkUtils.getUserAuthorities(user);
        if (authorities.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_ROLE, "User role not found");
        }

        FitLinkUserDetails userDetails = FitLinkUserDetails.builder()
                .publicId(user.getPublicId())
                .username(user.getUserName())
                .email(user.getEmail())
                .password(null)
                .authorities(authorities)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities)
        );

        log.info("Google login successful for email={}", user.getEmail());

        return TokenResponse.builder()
                .accessToken(jwtService.generateAccessToken(user))
                .refreshToken(jwtService.generateRefreshToken(user))
                .userName(user.getUserName())
                .role(authorities.stream().map(a -> a.getAuthority().toUpperCase()).collect(Collectors.toList()))
                .build();
    }

    private UserEntity findOrCreateGoogleUser(GoogleIdToken.Payload payload, String providerId, String email) {
        Optional<UserEntity> existingByEmail = userRepository.findByEmail(email);
        if (existingByEmail.isPresent()) {
            // Never silently convert an existing LOCAL account (or a Google account
            // registered under a different sub) into this Google identity.
            throw new AppException(
                    ErrorCode.ACCOUNT_CONFLICT,
                    "An account already exists for this email. Please sign in with your email and password instead."
            );
        }
        return createGoogleUser(payload, providerId, email);
    }

    private UserEntity createGoogleUser(GoogleIdToken.Payload payload, String providerId, String email) {
        Role defaultRole = roleRepository.findByRoleCode(Roles.UNASSIGNED)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_ROLE, "Default role not found"));

        UserEntity user = UserEntity.builder()
                .userName(resolveUserName(asString(payload.get("name")), email))
                .email(email)
                .passwordHash(placeholderPasswordHash())
                .status(UserStatus.ACTIVE)
                .emailVerified(Boolean.TRUE.equals(payload.getEmailVerified()))
                .provider(GOOGLE)
                .providerId(providerId)
                .build();
        userRepository.save(user);

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(defaultRole)
                .build();
        // Populate the inverse-side collection so the in-memory entity can be used
        // for authority/token generation without an extra DB round-trip.
        user.setRoles(new ArrayList<>(List.of(userRole)));
        userRoleRepository.save(userRole);

        log.info("Created new Google user email={}", email);
        return user;
    }

    private String resolveUserName(String googleName, String email) {
        String name = googleName == null ? "" : googleName.trim();
        if (name.length() < USERNAME_MIN_LENGTH) {
            int at = email.indexOf('@');
            name = at > 0 ? email.substring(0, at) : "";
        }
        if (name.length() < USERNAME_MIN_LENGTH) {
            name = "User";
        }
        return name.length() > USERNAME_MAX_LENGTH ? name.substring(0, USERNAME_MAX_LENGTH) : name;
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    /**
     * The password_hash column is NOT NULL. Google users have no password, so a
     * random unguessable BCrypt hash is stored as a safe placeholder — it can
     * never match a real password and never logs the user in via password.
     */
    private String placeholderPasswordHash() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return passwordEncoder.encode(HexFormat.of().formatHex(bytes));
    }
}
