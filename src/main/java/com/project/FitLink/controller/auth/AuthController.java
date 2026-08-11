package com.project.FitLink.controller.auth;

import com.project.FitLink.dto.Auth.*;
import com.project.FitLink.dto.GlobalResponse;
import com.project.FitLink.service.OtpService;
import com.project.FitLink.service.UserService;
import com.project.FitLink.service.authService;
import com.project.FitLink.service.googleAuthService;
import com.project.FitLink.utils.Constants;
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

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final authService authService;
    private final UserService userService;
    private final OtpService otpService;
    private final googleAuthService googleAuthService;

    @Operation(summary = "Register a new user",
            description = "Creates a new account and sends a 6-digit OTP to the email. Account stays inactive until OTP is verified.")
    @PostMapping("/register")
    public ResponseEntity<GlobalResponse> register(@RequestBody @Valid RegisterRequest registerRequest) {
        RegisterResponse result = userService.register(registerRequest);
        GlobalResponse response = new GlobalResponse();
        response.addMessage("message", result.getMessage());
        String expiration = Constants.OTP_EXPIRY_MINUTES + " minutes";
        response.addMessage("expiresIn", expiration);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Login with email and password",
            description = "Authenticates the user and returns access and refresh tokens. Email must be verified before login. Unknown email and wrong password return the same BAD_CREDENTIALS error intentionally.")
    @PostMapping("/login")
    public ResponseEntity<GlobalResponse> login(@RequestBody @Valid LoginRequest loginRequest) {
        TokenResponse result = authService.loginProcess(loginRequest);
        GlobalResponse response = new GlobalResponse();
        response.addMessage("message", "Login successful");
        response.addMessage("userName", result.getUserName());
        response.addMessage("role", result.getRole());
        response.addMessage("accessToken", result.getAccessToken());
        response.addMessage("refreshToken", result.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Sign in with Google",
            description = "Verifies a Google ID token obtained from Google Sign-In on the mobile client, finds or creates the matching user, and returns the application's own access and refresh tokens. The Google ID token is used once and is never the application token.")
    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> googleLogin(@RequestBody @Valid GoogleLoginRequest googleLoginRequest) {
        TokenResponse result = googleAuthService.authenticateWithGoogle(googleLoginRequest);
        GlobalResponse response = new GlobalResponse();
        response.addMessage("message", "Google login successful");
        response.addMessage("userName", result.getUserName());
        response.addMessage("role", result.getRole());
        response.addMessage("accessToken", result.getAccessToken());
        response.addMessage("refreshToken", result.getRefreshToken());
        var x = response.getApiResponse();
        return ResponseEntity.ok(x);
    }

    @Operation(summary = "Verify email OTP",
            description = "Verifies the 6-digit OTP sent after registration. OTP expires after 10 minutes. On success the account is activated and tokens are returned.")
    @PostMapping("/verify-otp")
    public ResponseEntity<GlobalResponse> verifyOtp(
            @Parameter(description = "Registered email address", required = true) @RequestParam @Email String email,
            @Parameter(description = "6-digit OTP received by email", required = true) @RequestParam String otpCode) {
        TokenResponse result = otpService.verifyEmail(email, otpCode);
        GlobalResponse response = new GlobalResponse();
        response.addMessage("message", "Email verified successfully");
        response.addMessage("accessToken", result.getAccessToken());
        response.addMessage("refreshToken", result.getRefreshToken());
        response.addMessage("userName", result.getUserName());
        response.addMessage("role", result.getRole());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Resend OTP",
            description = "Sends a new OTP to the email if registered. Always returns the same response regardless of whether the email exists. A 2-minute cooldown is enforced per user.")
    @PostMapping("/resend-otp")
    public ResponseEntity<GlobalResponse> resendOtp(
            @Parameter(description = "Email address", required = true) @RequestParam @Email String email,
            @Parameter(description = "OTP type: VERIFY for email verification, PASSWORD_RESET for password reset", required = true) @RequestParam OtpType otpType) {
        RegisterResponse result = otpService.resend(email, otpType);
        GlobalResponse response = new GlobalResponse();
        response.addMessage("message", result.getMessage());
        String expiration = Constants.OTP_EXPIRY_MINUTES + " minutes";
        response.addMessage("expiresIn", expiration);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Select user role",
            description = "Assigns a role (TRAINEE, COACH, or GYM) to a newly verified user whose current role is UNASSIGNED. Can only be called once. Invalidates previous tokens and returns new ones.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/select-role")
    public ResponseEntity<GlobalResponse> selectRole(@RequestBody @Valid SelectRoleRequest selectRoleRequest) {
        SelectRoleResponse result = authService.selectRole(selectRoleRequest);
        GlobalResponse response = new GlobalResponse();
        response.addMessage("message", result.getMessage());
        response.addMessage("role", result.getRole());
        response.addMessage("accessToken", result.getAccessToken());
        response.addMessage("refreshToken", result.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout",
            description = """
                    Clears the current server-side security context.

                    TEMPORARY BEHAVIOR — this endpoint does NOT revoke tokens on the server:
                    - Existing Access Tokens remain valid until expiration.
                    - Existing Refresh Tokens remain valid until expiration.
                    - The mobile client MUST delete both tokens from local storage immediately.

                    Real server-side logout will be implemented by deleting the Refresh Token session from Redis.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/logout")
    public ResponseEntity<GlobalResponse> logout() {
        RegisterResponse result = authService.logout();
        GlobalResponse response = new GlobalResponse();
        response.addMessage("message", result.getMessage());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Refresh access token",
            description = "Accepts a valid refresh token and returns a new access token. The refresh token is not rotated. On INVALID_REFRESH_TOKEN, redirect the user to login.")
    @PostMapping("/refresh-token")
    public ResponseEntity<GlobalResponse> refreshToken(@RequestBody @Valid RefreshRequest refreshRequest) {
        RefreshResponse result = authService.refreshToken(refreshRequest.getRefreshToken());
        GlobalResponse response = new GlobalResponse();
        response.addMessage("newAccessToken", result.getNewAccessToken());
        response.addMessage("message", "new access token generated, and old one is revoked");
        return ResponseEntity.ok(response);
    }
}
