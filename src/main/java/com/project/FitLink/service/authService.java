package com.project.FitLink.service;

import com.project.FitLink.auth.FitLinkUserDetails;
import com.project.FitLink.dto.Auth.LoginRequest;
import com.project.FitLink.dto.Auth.RefreshResponse;
import com.project.FitLink.dto.Auth.RegisterResponse;
import com.project.FitLink.dto.Auth.SelectRoleRequest;
import com.project.FitLink.dto.Auth.SelectRoleResponse;
import com.project.FitLink.dto.Auth.TokenResponse;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.entities.users.UserRole;
import com.project.FitLink.repository.users.UserRepository;
import com.project.FitLink.repository.users.UserRoleRepository;
import com.project.FitLink.utils.Constants;
import com.project.FitLink.utils.enums.Roles;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class authService {

    private final jwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository; // Added UserRoleRepository

    /**
     * Authenticates a user and generates access and refresh tokens.
     * @param loginRequest The login request containing email and password.
     * @return A TokenResponse containing the access and refresh tokens.
     * @throws BadCredentialsException If authentication fails due to invalid credentials.
     */
    public TokenResponse loginProcess(LoginRequest loginRequest) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(), loginRequest.getPassword()
        );
        Authentication auth = authenticationManager.authenticate(authentication);
        if(auth == null || !auth.isAuthenticated()) {
            log.warn("Login failed for email: {}", loginRequest.getEmail());
            throw new BadCredentialsException("Authentication Failed, Invalid username or password");
        }

        UserEntity user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        if (!user.isEmailVerified()) {
            throw new BadCredentialsException("Email not verified. Please verify your email first");
        }

        SecurityContextHolder.getContext().setAuthentication(auth);
        String accessToken = jwtService.generateAccessToken();
        String refreshToken = jwtService.generateRefreshToken();

        String userName = jwtService.extractClaims(accessToken).get("userName", String.class);
        String role = jwtService.extractClaims(accessToken).get("authorities", String.class);
        
        log.info("User {} logged in successfully", loginRequest.getEmail());
        
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userName(userName)
                .role(role)
                .build();
    }

    @Transactional
    public RegisterResponse logout() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (!(principal instanceof FitLinkUserDetails)) {
            throw new RuntimeException("Invalid authentication. User details not found.");
        }
        
        FitLinkUserDetails currentUser = (FitLinkUserDetails) principal;

        UserEntity user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        
        SecurityContextHolder.clearContext();

        log.info("User {} logged out successfully", currentUser.getEmail());
        
        return new RegisterResponse("Logged out successfully.");
    }

    /**
     * Refreshes the access token using a valid refresh token.
     * @param refreshToken The refresh token to use for refreshing the access token.
     * @return A RefreshResponse containing the new access token.
     * @throws RuntimeException If the refresh token is invalid or expired.
     */
    public RefreshResponse refreshToken(String refreshToken) {
        if(!jwtService.isTokenValid(refreshToken)) {
            throw new RuntimeException("Expired token, please login again");
        }
        
        Claims claims = jwtService.extractClaims(refreshToken);
        long id = claims.get("id", Long.class);
        String email = claims.get("email", String.class);
        String userName = claims.get("userName", String.class);
        String role = claims.get("authorities", String.class);

        if(!userRepository.existsByEmail(email)){
            throw new RuntimeException("User not found, please login again");
        }

        String newAccessToken = Jwts.builder()
                .issuer("FitLink")
                .subject("ACCESS Token")
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + Constants.JWT_ACCESS_TOKEN_EXPIRATION))
                .claim("id", id)
                .claim("email", email)
                .claim("userName", userName)
                .claim("authorities", role)
                .signWith(jwtService.getSecretKey())
                .compact();

        log.info("Access token refreshed for user: {}", email);
        
        return RefreshResponse.builder()
                .newAccessToken(newAccessToken)
                .build();
    }

    @Transactional
    public SelectRoleResponse selectRole(SelectRoleRequest selectRoleRequest) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof FitLinkUserDetails)) {
            throw new BadCredentialsException("Invalid authentication. User details not found.");
        }

        FitLinkUserDetails currentUser = (FitLinkUserDetails) principal;

        UserEntity user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // Check if the user's current role is UNASSIGNED
        Optional<UserRole> unassignedRoleOpt = user.getRoles().stream()
                .filter(userRole -> userRole.getRoleCode() == Roles.UNASSIGNED)
                .findFirst();

        if (unassignedRoleOpt.isEmpty()) {
            throw new BadCredentialsException("Role already assigned. Cannot change role.");
        }

        // Validate requested role against allowed values (TRAINEE, COACH, GYM)
        Roles newRole;
        try {
            newRole = Roles.valueOf(selectRoleRequest.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Invalid role specified. Allowed values are TRAINEE, COACH, GYM.");
        }

        if (newRole == Roles.ADMIN || newRole == Roles.SYSTEM || newRole == Roles.UNASSIGNED || newRole == Roles.USER) {
            throw new BadCredentialsException("Cannot select this role.");
        }

        // Update the user's role
        UserRole unassignedRole = unassignedRoleOpt.get();
        unassignedRole.setRoleCode(newRole);
        userRoleRepository.save(unassignedRole);

        // Increment tokenVersion
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        // Generate new tokens with the updated role and tokenVersion
        String newAccessToken = jwtService.generateAccessToken(user); // Assuming jwtService can take UserEntity
        String newRefreshToken = jwtService.generateRefreshToken(user); // Assuming jwtService can take UserEntity

        log.info("User {} selected role: {}", user.getEmail(), newRole);

        return SelectRoleResponse.builder()
                .role(newRole.name())
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .message("Role selected successfully.")
                .build();
    }
}
