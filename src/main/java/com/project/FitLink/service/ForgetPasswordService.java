package com.project.FitLink.service;

import com.project.FitLink.dto.Auth.RegisterResponse;
import com.project.FitLink.dto.Auth.VerifyResetOtpResponse;
import com.project.FitLink.entities.users.PasswordResetToken;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.exception.AppException;
import com.project.FitLink.exception.ErrorCode;
import com.project.FitLink.repository.users.PasswordResetTokenRepository;
import com.project.FitLink.repository.users.UserRepository;
import com.project.FitLink.utils.enums.OtpType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class ForgetPasswordService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;

    private static final int RESET_TOKEN_EXPIRY_MINUTES = 10;
    private static final int RESET_TOKEN_EXPIRY_SECONDS = RESET_TOKEN_EXPIRY_MINUTES * 60;

    // ─── Step 1: Send OTP ────────────────────────────────────────────────────

    @Transactional
    public RegisterResponse sendResetOtp(String email) {
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        UserEntity user = userRepository.findByEmail(normalized).orElse(null);

        if (user != null) {
            otpService.sendOtp(user, OtpType.PASSWORD_RESET);
            return new RegisterResponse("A password reset code has been sent to your email.");
        }

        throw new AppException(ErrorCode.USER_NOT_FOUND, "User not found with this email");

    }

    // ─── Step 2: Verify OTP → return reset token ─────────────────────────────

    @Transactional
    public VerifyResetOtpResponse verifyOtp(String email, String otpCode) {
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        UserEntity user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_OTP, "Invalid OTP"));

        otpService.consumeOtp(user, otpCode, OtpType.PASSWORD_RESET);

        resetTokenRepository.deleteByUser(user);

        String rawToken = generateSecureToken();
        String tokenHash = sha256(rawToken);

        resetTokenRepository.save(PasswordResetToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES))
                .build());

        return new VerifyResetOtpResponse(rawToken, RESET_TOKEN_EXPIRY_SECONDS);
    }

    // ─── Step 3: Reset password ───────────────────────────────────────────────

    @Transactional
    public RegisterResponse resetPassword(String rawToken, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH, "Passwords do not match");
        }

        String tokenHash = sha256(rawToken);
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_RESET_TOKEN, "Invalid or expired reset token"));

        if (resetToken.isExpired()) {
            resetTokenRepository.delete(resetToken);
            throw new AppException(ErrorCode.RESET_TOKEN_EXPIRED, "Reset token has expired");
        }

        if (resetToken.isUsed()) {
            throw new AppException(ErrorCode.RESET_TOKEN_USED, "Reset token has already been used");
        }

        UserEntity user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetTokenRepository.delete(resetToken);

        return new RegisterResponse("Password reset successfully.");
        // remove the old token (login again)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
