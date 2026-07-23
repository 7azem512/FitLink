package com.project.FitLink.controller.auth;

import com.project.FitLink.dto.Auth.*;
import com.project.FitLink.service.OtpService;
import com.project.FitLink.service.UserService;
import com.project.FitLink.service.authService;
import com.project.FitLink.utils.enums.OtpType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication (login, register, refresh token)")
public class AuthController {

    private final authService authService;
    private final UserService userService;
    private final OtpService otpService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.loginProcess(loginRequest));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody @Valid RegisterRequest registerRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(registerRequest));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<TokenResponse> verifyOtp(@RequestParam @Email String email, @RequestParam String otpCode) {
        return ResponseEntity.ok(otpService.verifyEmail(email, otpCode));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<RegisterResponse> resendOtp(
            @RequestParam @Email String email,
            @RequestParam(defaultValue = "DEFAULT") OtpType otpType) {
        return ResponseEntity.ok(otpService.resend(email, otpType));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<RegisterResponse> forgotPassword(@RequestParam @Email String email) {
        return ResponseEntity.ok(otpService.sendResetOtp(email));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<RegisterResponse> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return ResponseEntity.ok(otpService.resetPassword(request.getEmail(), request.getOtpCode(), request.getNewPassword()));
    }

    @PostMapping("/logout")
    public ResponseEntity<RegisterResponse> logout() {
        return ResponseEntity.ok(authService.logout());
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<RefreshResponse> refreshToken(@RequestBody @Valid RefreshRequest refreshRequest) {
        return ResponseEntity.ok(authService.refreshToken(refreshRequest.getRefreshToken()));
    }
}
