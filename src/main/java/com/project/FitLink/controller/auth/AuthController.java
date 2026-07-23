package com.project.FitLink.controller.auth;

import com.project.FitLink.dto.Auth.*;
import com.project.FitLink.exception.ErrorResponse;
import com.project.FitLink.service.OtpService;
import com.project.FitLink.service.UserService;
import com.project.FitLink.service.authService;
import com.project.FitLink.utils.enums.OtpType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Handles registration, login, OTP verification, password reset, token refresh, and logout.")
public class AuthController {

    private final authService authService;
    private final UserService userService;
    private final OtpService otpService;

    // ─────────────────────────────────────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            operationId = "register",
            summary = "Register a new user",
            description = "Creates a new user account and sends a 6-digit OTP to the provided email for verification. The account remains inactive until the OTP is verified."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registration successful. OTP sent to email.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RegisterResponse.class),
                            examples = @ExampleObject(value = "{\"message\": \"Registration successful. Please check your email for the OTP.\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed (missing fields, invalid email format, weak password, or passwords do not match).",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"email\": \"Invalid email format\", \"password\": \"Password must contain at least one letter and one number\"}}"))),
            @ApiResponse(responseCode = "409", description = "Email is already registered.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"email\": \"Email already exists\"}}")))
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody @Valid RegisterRequest registerRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(registerRequest));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            operationId = "login",
            summary = "Login with email and password",
            description = "Authenticates the user and returns a JWT access token and refresh token. The account must have a verified email before login is allowed."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TokenResponse.class),
                            examples = @ExampleObject(value = "{\"accessToken\": \"eyJ...fake_access\", \"refreshToken\": \"eyJ...fake_refresh\", \"userName\": \"John Doe\", \"role\": \"ROLE_USER\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed (blank email or password).",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"email\": \"Email is required\"}}"))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or email not verified.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"error\": \"Email not verified. Please verify your email first\"}}")))
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.loginProcess(loginRequest));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VERIFY OTP
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            operationId = "verifyOtp",
            summary = "Verify email using OTP",
            description = "Verifies the 6-digit OTP sent to the user's email after registration. On success, the account is activated and JWT tokens are returned immediately so the user is logged in."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP verified. Account activated. Tokens returned.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TokenResponse.class),
                            examples = @ExampleObject(value = "{\"accessToken\": \"eyJ...fake_access\", \"refreshToken\": \"eyJ...fake_refresh\", \"userName\": \"John Doe\", \"role\": \"ROLE_USER\"}"))),
            @ApiResponse(responseCode = "409", description = "User not found, OTP is invalid, or OTP has expired.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"error\": \"Invalid OTP\"}}")))
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<TokenResponse> verifyOtp(
            @Parameter(description = "Email address used during registration", example = "john.doe@example.com", required = true)
            @RequestParam @Email String email,
            @Parameter(description = "6-digit OTP received by email", example = "482910", required = true)
            @RequestParam String otpCode) {
        return ResponseEntity.ok(otpService.verifyEmail(email, otpCode));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESEND OTP
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            operationId = "resendOtp",
            summary = "Resend OTP to email",
            description = "Deletes the previous OTP and sends a new one to the user's email. A 2-minute cooldown is enforced between requests. Use otpType=DEFAULT for email verification."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New OTP sent successfully.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RegisterResponse.class),
                            examples = @ExampleObject(value = "{\"message\": \"OTP resent successfully. Please check your email.\"}"))),
            @ApiResponse(responseCode = "409", description = "User not found or cooldown period has not elapsed.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"error\": \"Please wait 2 minutes before requesting a new OTP\"}}")))
    })
    @PostMapping("/resend-otp")
    public ResponseEntity<RegisterResponse> resendOtp(
            @Parameter(description = "Email address of the user", example = "john.doe@example.com", required = true)
            @RequestParam @Email String email,
            @Parameter(description = "OTP type. Use DEFAULT for email verification, RESET for password reset.", example = "DEFAULT", schema = @Schema(implementation = OtpType.class))
            @RequestParam(defaultValue = "DEFAULT") OtpType otpType) {
        return ResponseEntity.ok(otpService.resend(email, otpType));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SELECT ROLE
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            operationId = "selectRole",
            summary = "Select a role for the user",
            description = "Allows a newly registered and verified user to select their role (TRAINEE, COACH, or GYM). This endpoint can only be called once when the user's current role is UNASSIGNED. Upon successful role selection, new access and refresh tokens are generated and returned, invalidating previous tokens.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role selected successfully. New tokens returned.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SelectRoleResponse.class),
                            examples = @ExampleObject(value = "{\"role\": \"COACH\", \"accessToken\": \"eyJ...new_access\", \"refreshToken\": \"eyJ...new_refresh\", \"message\": \"Role selected successfully.\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed (invalid role value).",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"role\": \"Invalid role. Allowed values are TRAINEE, COACH, GYM.\"}}"))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: Missing, invalid, or expired Bearer token.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"error\": \"Unauthorized\"}}"))),
            @ApiResponse(responseCode = "403", description = "Forbidden: User already has a role assigned or tried to select an unsupported role.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"error\": \"Role already assigned or invalid role selection.\"}}"))),
            @ApiResponse(responseCode = "404", description = "User not found.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"error\": \"User not found.\"}}")))
    })
    @PatchMapping("/select-role")
    public ResponseEntity<SelectRoleResponse> selectRole(@RequestBody @Valid SelectRoleRequest selectRoleRequest) {
        return ResponseEntity.ok(authService.selectRole(selectRoleRequest));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOGOUT
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            operationId = "logout",
            summary = "Logout the current user",
            description = "Increments the user's tokenVersion in the database, which immediately invalidates all existing access and refresh tokens across all devices. Requires a valid Bearer token.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged out successfully. All tokens are now invalid.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RegisterResponse.class),
                            examples = @ExampleObject(value = "{\"message\": \"Logged out successfully.\"}"))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired Bearer token.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"error\": \"Invalid or expired JWT token\"}")))
    })
    @PostMapping("/logout")
    public ResponseEntity<RegisterResponse> logout() {
        return ResponseEntity.ok(authService.logout());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REFRESH TOKEN
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            operationId = "refreshToken",
            summary = "Refresh the access token",
            description = "Accepts a valid refresh token and returns a new access token. The refresh token itself is not rotated. If the token is expired or the user no longer exists, an error is returned."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New access token generated.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RefreshResponse.class),
                            examples = @ExampleObject(value = "{\"newAccessToken\": \"eyJ...fake_new_access_token\"}"))),
            @ApiResponse(responseCode = "409", description = "Refresh token is expired, invalid, or user no longer exists.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"error\": \"Expired token, please login again\"}}")))
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<RefreshResponse> refreshToken(@RequestBody @Valid RefreshRequest refreshRequest) {
        return ResponseEntity.ok(authService.refreshToken(refreshRequest.getRefreshToken()));
    }
}