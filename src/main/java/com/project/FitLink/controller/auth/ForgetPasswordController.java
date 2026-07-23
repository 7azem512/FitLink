package com.project.FitLink.controller.auth;

import com.project.FitLink.dto.Auth.*;
import com.project.FitLink.exception.ErrorResponse;
import com.project.FitLink.service.ForgetPasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/forget-password")
@RequiredArgsConstructor
@Tag(name = "Password Reset", description = "Three-step password reset flow: request OTP → verify OTP → reset password.")
public class ForgetPasswordController {

    private final ForgetPasswordService forgetPasswordService;

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 1 – Request OTP
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            operationId = "forgetPasswordRequestOtp",
            summary = "Step 1 – Request a password-reset OTP",
            description = "Sends a 6-digit OTP to the user's email if the address is registered. " +
                    "The OTP is valid for 5 minutes. Any previous password-reset OTP for the same user is deleted. " +
                    "Always returns the same response to avoid revealing whether the email exists."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request processed. OTP sent if email is registered.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RegisterResponse.class),
                            examples = @ExampleObject(value = "{\"message\": \"If the email is registered, a password reset code has been sent.\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"email\": \"Invalid email format\"}}"))),
            @ApiResponse(responseCode = "429", description = "Too many requests.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"status\": 429, \"error\": \"Too Many Requests\", \"message\": \"Too many requests. Please try again later.\"}")))
    })
    @PostMapping
    public ResponseEntity<RegisterResponse> requestOtp(
            @RequestBody @Valid ForgotPasswordRequest request) {
        return ResponseEntity.ok(forgetPasswordService.sendResetOtp(request.getEmail()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 2 – Verify OTP
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            operationId = "forgetPasswordVerifyOtp",
            summary = "Step 2 – Verify the password-reset OTP",
            description = "Validates the 6-digit OTP sent in step 1. " +
                    "If valid, the OTP is consumed immediately and a single-use reset token is returned. " +
                    "The reset token is valid for 10 minutes and must be passed to /forget-password/reset. " +
                    "Only the SHA-256 hash of the token is stored in the database."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP verified. Reset token returned.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = VerifyResetOtpResponse.class),
                            examples = @ExampleObject(value = "{\"resetToken\": \"a3f8c2d1e9b74f6a8c2d1e9b74f6a8c2\", \"expiresIn\": 600}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"otpCode\": \"OTP must be exactly 6 digits\"}}"))),
            @ApiResponse(responseCode = "409", description = "OTP not found, invalid, or expired.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"error\": \"Invalid OTP\"}}"))),
            @ApiResponse(responseCode = "429", description = "Too many requests.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"status\": 429, \"error\": \"Too Many Requests\", \"message\": \"Too many requests. Please try again later.\"}")))
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<VerifyResetOtpResponse> verifyOtp(
            @RequestBody @Valid VerifyResetOtpRequest request) {
        return ResponseEntity.ok(forgetPasswordService.verifyOtp(request.getEmail(), request.getOtpCode()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 3 – Reset Password
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            operationId = "forgetPasswordReset",
            summary = "Step 3 – Reset the password",
            description = "Sets a new password using the reset token returned from step 2. " +
                    "The token is validated by hashing it with SHA-256 and looking it up in the database. " +
                    "After a successful reset, the token is deleted and tokenVersion is incremented to invalidate all existing JWT tokens."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successfully.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RegisterResponse.class),
                            examples = @ExampleObject(value = "{\"message\": \"Password reset successfully.\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed or passwords do not match.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"error\": \"Passwords do not match\"}}"))),
            @ApiResponse(responseCode = "409", description = "Reset token not found, expired, or already used.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"errorMessages\": {\"error\": \"Invalid or expired reset token\"}}"))),
            @ApiResponse(responseCode = "429", description = "Too many requests.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"status\": 429, \"error\": \"Too Many Requests\", \"message\": \"Too many requests. Please try again later.\"}")))
    })
    @PostMapping("/reset")
    public ResponseEntity<RegisterResponse> resetPassword(
            @RequestBody @Valid ResetPasswordRequest request) {
        return ResponseEntity.ok(forgetPasswordService.resetPassword(
                request.getResetToken(), request.getNewPassword(), request.getConfirmPassword()));
    }
}
