package com.project.FitLink.service;

import com.project.FitLink.dto.Auth.RegisterResponse;
import com.project.FitLink.dto.Auth.VerifyResetOtpResponse;
import com.project.FitLink.entities.users.OTP;
import com.project.FitLink.entities.users.PasswordResetToken;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.repository.users.OtpRepository;
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
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class ForgetPasswordService {

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int RESET_TOKEN_EXPIRY_MINUTES = 10;
    private static final int RESET_TOKEN_EXPIRY_SECONDS = RESET_TOKEN_EXPIRY_MINUTES * 60;

    // ─── Step 1: Send OTP ────────────────────────────────────────────────────

    @Transactional
    public RegisterResponse sendResetOtp(String email) {
        String normalized = email.trim().toLowerCase();
        UserEntity user = userRepository.findByEmail(normalized).orElse(null);

        if (user != null) {
            otpRepository.deleteByUserAndOtpType(user, OtpType.PASSWORD_RESET);

            String otpCode = String.format("%06d", new SecureRandom().nextInt(1_000_000));
            otpRepository.save(OTP.builder()
                    .otpCode(otpCode)
                    .user(user)
                    .otpType(OtpType.PASSWORD_RESET)
                    .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                    .build());

            emailService.sendOtpEmail(user.getEmail(), user.getUserName(), otpCode);
        }

        return new RegisterResponse("If the email is registered, a password reset code has been sent.");
    }

    // ─── Step 2: Verify OTP → return reset token ─────────────────────────────

    @Transactional
    public VerifyResetOtpResponse verifyOtp(String email, String otpCode) {
        String normalized = email.trim().toLowerCase();
        UserEntity user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new RuntimeException("Invalid OTP"));

        OTP otp = otpRepository.findByUserAndOtpCodeAndOtpType(user, otpCode, OtpType.PASSWORD_RESET)
                .orElseThrow(() -> new RuntimeException("Invalid OTP"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpRepository.delete(otp);
            throw new RuntimeException("OTP has expired");
        }

        otpRepository.delete(otp);

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
            throw new RuntimeException("Passwords do not match");
        }

        String tokenHash = sha256(rawToken);
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        if (resetToken.isExpired()) {
            resetTokenRepository.delete(resetToken);
            throw new RuntimeException("Reset token has expired");
        }

        if (resetToken.isUsed()) {
            throw new RuntimeException("Reset token has already been used");
        }

        UserEntity user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        resetTokenRepository.delete(resetToken);

        return new RegisterResponse("Password reset successfully.");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
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
