package com.project.FitLink.controller.auth;

import com.project.FitLink.dto.Auth.*;
import com.project.FitLink.service.OtpService;
import com.project.FitLink.service.UserService;
import com.project.FitLink.service.authService;
import com.project.FitLink.utils.enums.OtpType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@Tag(name = "Authentication")
public class AuthController {

    private final authService authService;
    private final UserService userService;
    private final OtpService otpService;

    @Operation(summary = "Register a new user",
            description = "Creates a new account and sends a 6-digit OTP to the email. Account stays inactive until OTP is verified.")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody @Valid RegisterRequest registerRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(registerRequest));
    }

    @Operation(summary = "Login with email and password",
            description = "Authenticates the user and returns access and refresh tokens. Email must be verified before login. Unknown email and wrong password return the same BAD_CREDENTIALS error intentionally.")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.loginProcess(loginRequest));
    }

    @Operation(summary = "Verify email OTP",
            description = "Verifies the 6-digit OTP sent after registration. OTP expires after 10 minutes. On success the account is activated and tokens are returned.")
    @PostMapping("/verify-otp")
    public ResponseEntity<TokenResponse> verifyOtp(
            @Parameter(description = "Registered email address", required = true) @RequestParam @Email String email,
            @Parameter(description = "6-digit OTP received by email", required = true) @RequestParam String otpCode) {
        return ResponseEntity.ok(otpService.verifyEmail(email, otpCode));
    }

    @Operation(summary = "Resend OTP",
            description = "Sends a new OTP to the email if registered. Always returns the same response regardless of whether the email exists. A 2-minute cooldown is enforced per user.")
    @PostMapping("/resend-otp")
    public ResponseEntity<RegisterResponse> resendOtp(
            @Parameter(description = "Email address", required = true) @RequestParam @Email String email,
            @Parameter(description = "OTP type: DEFAULT for email verification, PASSWORD_RESET for password reset") @RequestParam(defaultValue = "DEFAULT") OtpType otpType) {
        return ResponseEntity.ok(otpService.resend(email, otpType));
    }

    @Operation(summary = "Select user role",
            description = "Assigns a role (TRAINEE, COACH, or GYM) to a newly verified user whose current role is UNASSIGNED. Can only be called once. Invalidates previous tokens and returns new ones.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/select-role")
    public ResponseEntity<SelectRoleResponse> selectRole(@RequestBody @Valid SelectRoleRequest selectRoleRequest) {
        return ResponseEntity.ok(authService.selectRole(selectRoleRequest));
    }

    @Operation(summary = "Logout",
            description = "Increments tokenVersion to invalidate all existing tokens across all devices.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/logout")
    public ResponseEntity<RegisterResponse> logout() {
        return ResponseEntity.ok(authService.logout());
    }

    @Operation(summary = "Refresh access token",
            description = "Accepts a valid refresh token and returns a new access token. The refresh token is not rotated. On INVALID_REFRESH_TOKEN, redirect the user to login.")
    @PostMapping("/refresh-token")
    public ResponseEntity<RefreshResponse> refreshToken(@RequestBody @Valid RefreshRequest refreshRequest) {
        return ResponseEntity.ok(authService.refreshToken(refreshRequest.getRefreshToken()));
    }
}
