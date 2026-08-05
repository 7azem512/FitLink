package com.project.FitLink.controller.auth;

import com.project.FitLink.dto.Auth.*;
import com.project.FitLink.service.ForgetPasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/forget-password")
@RequiredArgsConstructor
@Tag(name = "Password Reset", description = "Three-step flow: request OTP → verify OTP → reset password.")
public class ForgetPasswordController {

    private final ForgetPasswordService forgetPasswordService;

    @Operation(summary = "Step 1 – Request password-reset OTP",
            description = "Sends a 6-digit OTP to the email if registered. OTP expires in 5 minutes. Always returns the same response to avoid revealing whether the email exists.")
    @PostMapping
    public ResponseEntity<RegisterResponse> requestOtp(@RequestBody @Valid ForgotPasswordRequest request) {
        return ResponseEntity.ok(forgetPasswordService.sendResetOtp(request.getEmail()));
    }

    @Operation(summary = "Step 2 – Verify password-reset OTP",
            description = "Validates the OTP from step 1. On success the OTP is consumed and a single-use reset token valid for 10 minutes is returned. Only the SHA-256 hash of the token is stored in the database.")
    @PostMapping("/verify-otp")
    public ResponseEntity<VerifyResetOtpResponse> verifyOtp(@RequestBody @Valid VerifyResetOtpRequest request) {
        return ResponseEntity.ok(forgetPasswordService.verifyOtp(request.getEmail(), request.getOtpCode()));
    }

    @Operation(summary = "Step 3 – Reset password",
            description = "Sets a new password using the reset token from step 2. Token is validated via SHA-256 hash lookup. On success the token is deleted and the new password is saved.")
    @PostMapping("/reset")
    public ResponseEntity<RegisterResponse> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return ResponseEntity.ok(forgetPasswordService.resetPassword(
                request.getResetToken(), request.getNewPassword(), request.getConfirmPassword()));
    }
}
