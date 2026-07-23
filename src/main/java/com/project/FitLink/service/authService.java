package com.project.FitLink.service;


import com.project.FitLink.auth.FitLinkUserDetails;
import com.project.FitLink.dto.Auth.LoginRequest;
import com.project.FitLink.dto.Auth.RefreshResponse;
import com.project.FitLink.dto.Auth.RegisterResponse;
import com.project.FitLink.dto.Auth.TokenResponse;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.repository.users.UserRepository;
import com.project.FitLink.utils.Constants;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class authService {

    private final jwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final TokenCacheService tokenCacheService;

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
        FitLinkUserDetails currentUser = (FitLinkUserDetails) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();

        UserEntity user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        
        String token = ((UsernamePasswordAuthenticationToken) SecurityContextHolder
                .getContext().getAuthentication()).getCredentials().toString();
        tokenCacheService.addToBlacklist(token, currentUser.getId(), "User logout");
        
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
        
        if (tokenCacheService.isTokenBlacklisted(refreshToken)) {
            throw new RuntimeException("Token has been revoked, please login again");
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

}

