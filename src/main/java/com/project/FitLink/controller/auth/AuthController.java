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
import io.swagger.v3.oas.annotations.headers.Header;
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
@Tag(name = "Authentication", description = "Mobile flow: register, verify the email OTP, select a role, then use the returned access and refresh tokens. Use ErrorResponse.code for client behavior. 401 means authentication is missing or invalid; 403 means the account or requested action is not allowed.")
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
            @ApiResponse(responseCode = "400", description = "Validation, malformed request, or password-confirmation failure.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"VALIDATION_ERROR\",\"message\":\"Request validation failed\",\"path\":\"/auth/register\",\"errors\":{\"email\":\"Invalid email format\"}}"),
                                    @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"MALFORMED_REQUEST\",\"message\":\"Malformed JSON request or invalid data type\",\"path\":\"/auth/register\"}"),
                                    @ExampleObject(name = "PASSWORD_MISMATCH", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"PASSWORD_MISMATCH\",\"message\":\"Passwords do not match\",\"path\":\"/auth/register\"}")
                            })),
            @ApiResponse(responseCode = "409", description = "Email conflict or database integrity conflict.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "DUPLICATE_EMAIL", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":409,\"code\":\"DUPLICATE_EMAIL\",\"message\":\"Email already exists\",\"path\":\"/auth/register\"}"),
                                    @ExampleObject(name = "DATA_INTEGRITY_VIOLATION", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":409,\"code\":\"DATA_INTEGRITY_VIOLATION\",\"message\":\"A data conflict occurred.\",\"path\":\"/auth/register\"}")
                            })),
            @ApiResponse(responseCode = "429", description = "Registration request rate limit exceeded. Retry after the Retry-After delay.",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait before retrying", schema = @Schema(type = "integer", example = "60")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(name = "RATE_LIMITED", value = "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":500,\"code\":\"INTERNAL_ERROR\",\"message\":\"An unexpected error occurred\",\"path\":\"/auth/register\"}")))
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
            description = "Authenticates the user and returns an access token and refresh token. Store both only in platform secure storage. The refresh token is not rotated; use it only with /auth/refresh-token. Unknown email and wrong password intentionally return the same BAD_CREDENTIALS response. The account must have a verified email before login is allowed."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TokenResponse.class),
                            examples = @ExampleObject(value = "{\"accessToken\": \"eyJ...fake_access\", \"refreshToken\": \"eyJ...fake_refresh\", \"userName\": \"John Doe\", \"role\": \"ROLE_USER\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation or malformed request.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"VALIDATION_ERROR\",\"message\":\"Request validation failed\",\"path\":\"/auth/login\",\"errors\":{\"email\":\"Email is required\"}}"),
                                    @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"MALFORMED_REQUEST\",\"message\":\"Malformed JSON request or invalid data type\",\"path\":\"/auth/login\"}")
                            })),
            @ApiResponse(responseCode = "401", description = "Unknown email and wrong password return the same response.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "BAD_CREDENTIALS", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":401,\"code\":\"BAD_CREDENTIALS\",\"message\":\"Invalid email or password\",\"path\":\"/auth/login\"}"))),
            @ApiResponse(responseCode = "403", description = "Credentials are valid but the email is not verified.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "EMAIL_NOT_VERIFIED", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":403,\"code\":\"EMAIL_NOT_VERIFIED\",\"message\":\"Email not verified. Please verify your email first\",\"path\":\"/auth/login\"}"))),
            @ApiResponse(responseCode = "429", description = "Login request rate limit exceeded. Retry after the Retry-After delay.",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait before retrying", schema = @Schema(type = "integer", example = "60")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(name = "RATE_LIMITED", value = "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":500,\"code\":\"INTERNAL_ERROR\",\"message\":\"An unexpected error occurred\",\"path\":\"/auth/login\"}")))
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
            description = "Verifies the 6-digit OTP sent to the user's email after registration. The OTP expires after 10 minutes. On success, the account is activated and access and refresh tokens are returned immediately; store them only in platform secure storage."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP verified. Account activated. Tokens returned.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TokenResponse.class),
                            examples = @ExampleObject(value = "{\"accessToken\": \"eyJ...fake_access\", \"refreshToken\": \"eyJ...fake_refresh\", \"userName\": \"John Doe\", \"role\": \"ROLE_USER\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation, malformed request, or invalid OTP.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"VALIDATION_ERROR\",\"message\":\"Request validation failed\",\"path\":\"/auth/verify-otp\",\"errors\":{\"email\":\"Invalid email format\"}}"),
                                    @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"MALFORMED_REQUEST\",\"message\":\"Required request parameter is missing\",\"path\":\"/auth/verify-otp\"}"),
                                    @ExampleObject(name = "INVALID_OTP", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"INVALID_OTP\",\"message\":\"Invalid OTP\",\"path\":\"/auth/verify-otp\"}")
                            })),
            @ApiResponse(responseCode = "404", description = "User not found.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "USER_NOT_FOUND", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":404,\"code\":\"USER_NOT_FOUND\",\"message\":\"User not found\",\"path\":\"/auth/verify-otp\"}"))),
            @ApiResponse(responseCode = "410", description = "OTP has expired.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "OTP_EXPIRED", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":410,\"code\":\"OTP_EXPIRED\",\"message\":\"OTP has expired\",\"path\":\"/auth/verify-otp\"}"))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":500,\"code\":\"INTERNAL_ERROR\",\"message\":\"An unexpected error occurred\",\"path\":\"/auth/verify-otp\"}")))
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
            description = "Deletes the previous OTP and sends a new one when the email is registered. The same success response is returned for existing and unknown emails. A 2-minute cooldown is enforced for existing users. On OTP_RESEND_COOLDOWN, do not retry until the cooldown ends. Use otpType=DEFAULT for email verification."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request processed without revealing whether the email exists.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RegisterResponse.class),
                            examples = @ExampleObject(value = "{\"message\": \"OTP resent successfully. Please check your email.\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation or malformed request.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"VALIDATION_ERROR\",\"message\":\"Request validation failed\",\"path\":\"/auth/resend-otp\",\"errors\":{\"email\":\"Invalid email format\"}}"),
                                    @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"MALFORMED_REQUEST\",\"message\":\"Request parameter has an invalid value\",\"path\":\"/auth/resend-otp\"}")
                            })),
            @ApiResponse(responseCode = "429", description = "Cooldown period has not elapsed for an existing user.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "OTP_RESEND_COOLDOWN", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":429,\"code\":\"OTP_RESEND_COOLDOWN\",\"message\":\"Please wait 2 minutes before requesting a new OTP\",\"path\":\"/auth/resend-otp\"}"))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":500,\"code\":\"INTERNAL_ERROR\",\"message\":\"An unexpected error occurred\",\"path\":\"/auth/resend-otp\"}")))
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
            @ApiResponse(responseCode = "400", description = "Validation, malformed request, or invalid role value.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"VALIDATION_ERROR\",\"message\":\"Request validation failed\",\"path\":\"/auth/select-role\",\"errors\":{\"role\":\"Invalid role. Allowed values are TRAINEE, COACH, GYM.\"}}"),
                                    @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"MALFORMED_REQUEST\",\"message\":\"Malformed JSON request or invalid data type\",\"path\":\"/auth/select-role\"}"),
                                    @ExampleObject(name = "INVALID_ROLE", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"INVALID_ROLE\",\"message\":\"Invalid role specified. Allowed values are TRAINEE, COACH, GYM.\",\"path\":\"/auth/select-role\"}")
                            })),
            @ApiResponse(responseCode = "401", description = "Unauthorized: Missing, invalid, or expired Bearer token.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "UNAUTHORIZED", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication is required.\",\"path\":\"/auth/select-role\"}"))),
            @ApiResponse(responseCode = "403", description = "The requested role is not allowed.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ROLE_NOT_ALLOWED", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":403,\"code\":\"ROLE_NOT_ALLOWED\",\"message\":\"Cannot select this role.\",\"path\":\"/auth/select-role\"}"))),
            @ApiResponse(responseCode = "404", description = "User not found.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "USER_NOT_FOUND", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":404,\"code\":\"USER_NOT_FOUND\",\"message\":\"User not found\",\"path\":\"/auth/select-role\"}"))),
            @ApiResponse(responseCode = "409", description = "A role has already been assigned.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ROLE_ALREADY_ASSIGNED", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":409,\"code\":\"ROLE_ALREADY_ASSIGNED\",\"message\":\"Role already assigned. Cannot change role.\",\"path\":\"/auth/select-role\"}"))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":500,\"code\":\"INTERNAL_ERROR\",\"message\":\"An unexpected error occurred\",\"path\":\"/auth/select-role\"}")))
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
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "UNAUTHORIZED", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication is required.\",\"path\":\"/auth/logout\"}"),
                                    @ExampleObject(name = "INVALID_JWT", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"Invalid or expired JWT token.\",\"path\":\"/auth/logout\"}")
                            })),
            @ApiResponse(responseCode = "404", description = "Authenticated user no longer exists.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "USER_NOT_FOUND", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":404,\"code\":\"USER_NOT_FOUND\",\"message\":\"User not found\",\"path\":\"/auth/logout\"}"))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":500,\"code\":\"INTERNAL_ERROR\",\"message\":\"An unexpected error occurred\",\"path\":\"/auth/logout\"}")))
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
            description = "Accepts a valid refresh token and returns a new access token. The refresh token itself is not rotated. Store the refresh token only in platform secure storage. On INVALID_REFRESH_TOKEN, clear local credentials and return the user to login."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New access token generated.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RefreshResponse.class),
                            examples = @ExampleObject(value = "{\"newAccessToken\": \"eyJ...fake_new_access_token\"}"))),
            @ApiResponse(responseCode = "400", description = "Malformed request.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":400,\"code\":\"MALFORMED_REQUEST\",\"message\":\"Malformed JSON request or invalid data type\",\"path\":\"/auth/refresh-token\"}"))),
            @ApiResponse(responseCode = "401", description = "Refresh token is expired, invalid, or no longer associated with a user.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REFRESH_TOKEN", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":401,\"code\":\"INVALID_REFRESH_TOKEN\",\"message\":\"Expired token, please login again\",\"path\":\"/auth/refresh-token\"}"))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\":\"2026-07-24T01:30:00\",\"status\":500,\"code\":\"INTERNAL_ERROR\",\"message\":\"An unexpected error occurred\",\"path\":\"/auth/refresh-token\"}")))
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<RefreshResponse> refreshToken(@RequestBody @Valid RefreshRequest refreshRequest) {
        return ResponseEntity.ok(authService.refreshToken(refreshRequest.getRefreshToken()));
    }
}
