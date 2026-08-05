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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForgetPasswordServiceTest {

    @Mock UserRepository userRepository;
    @Mock OtpRepository otpRepository;
    @Mock PasswordResetTokenRepository resetTokenRepository;
    @Mock EmailService emailService;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks ForgetPasswordService service;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = UserEntity.builder()
                .id(1L)
                .email("ahmed@example.com")
                .userName("Ahmed")
                .passwordHash("hashed")
                .build();
    }

    // ── Test 1: Send OTP successfully ─────────────────────────────────────────

    @Test
    void sendResetOtp_existingEmail_sendsOtpAndReturnsGenericMessage() {
        when(userRepository.findByEmail("ahmed@example.com")).thenReturn(Optional.of(user));

        RegisterResponse response = service.sendResetOtp("ahmed@example.com");

        verify(otpRepository).deleteByUserAndOtpType(user, OtpType.PASSWORD_RESET);
        verify(otpRepository).save(any(OTP.class));
        verify(emailService).sendVerifyEmailOtp(eq("ahmed@example.com"), eq("Ahmed"), anyString());
        assertThat(response.getMessage()).contains("If the email is registered");
    }

    @Test
    void sendResetOtp_unknownEmail_returnsGenericMessageWithoutSendingEmail() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        RegisterResponse response = service.sendResetOtp("unknown@example.com");

        verify(emailService, never()).sendVerifyEmailOtp(any(), any(), any());
        assertThat(response.getMessage()).contains("If the email is registered");
    }

    // ── Test 2: Reject invalid OTP ────────────────────────────────────────────

    @Test
    void verifyOtp_invalidCode_throwsException() {
        when(userRepository.findByEmail("ahmed@example.com")).thenReturn(Optional.of(user));
        when(otpRepository.findByUserAndOtpCodeAndOtpType(user, "000000", OtpType.PASSWORD_RESET))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOtp("ahmed@example.com", "000000"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid OTP");
    }

    // ── Test 3: Reject expired OTP ────────────────────────────────────────────

    @Test
    void verifyOtp_expiredOtp_deletesAndThrowsException() {
        OTP expiredOtp = OTP.builder()
                .otpCode("123456")
                .user(user)
                .otpType(OtpType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(userRepository.findByEmail("ahmed@example.com")).thenReturn(Optional.of(user));
        when(otpRepository.findByUserAndOtpCodeAndOtpType(user, "123456", OtpType.PASSWORD_RESET))
                .thenReturn(Optional.of(expiredOtp));

        assertThatThrownBy(() -> service.verifyOtp("ahmed@example.com", "123456"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("OTP has expired");

        verify(otpRepository).delete(expiredOtp);
    }

    // ── Test 4: Return reset token after valid OTP ────────────────────────────

    @Test
    void verifyOtp_validOtp_deletesOtpAndReturnsResetToken() {
        OTP validOtp = OTP.builder()
                .otpCode("123456")
                .user(user)
                .otpType(OtpType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusMinutes(4))
                .build();

        when(userRepository.findByEmail("ahmed@example.com")).thenReturn(Optional.of(user));
        when(otpRepository.findByUserAndOtpCodeAndOtpType(user, "123456", OtpType.PASSWORD_RESET))
                .thenReturn(Optional.of(validOtp));

        VerifyResetOtpResponse response = service.verifyOtp("ahmed@example.com", "123456");

        verify(otpRepository).delete(validOtp);
        verify(resetTokenRepository).deleteByUser(user);
        verify(resetTokenRepository).save(any(PasswordResetToken.class));
        assertThat(response.getResetToken()).isNotBlank();
        assertThat(response.getExpiresIn()).isEqualTo(600);
    }

    // ── Test 5: Reject invalid reset token ───────────────────────────────────

    @Test
    void resetPassword_invalidToken_throwsException() {
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("bad-token", "NewPass99", "NewPass99"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid or expired reset token");
    }

    // ── Test 6: Reject expired reset token ───────────────────────────────────

    @Test
    void resetPassword_expiredToken_throwsException() {
        String rawToken = "some-token";
        PasswordResetToken expired = PasswordResetToken.builder()
                .tokenHash(sha256(rawToken))
                .user(user)
                .createdAt(LocalDateTime.now().minusMinutes(15))
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .build();

        when(resetTokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resetPassword(rawToken, "NewPass99", "NewPass99"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Reset token has expired");

        verify(resetTokenRepository).delete(expired);
    }

    // ── Test 7: Reject used reset token ──────────────────────────────────────

    @Test
    void resetPassword_usedToken_throwsException() {
        String rawToken = "used-token";
        PasswordResetToken used = PasswordResetToken.builder()
                .tokenHash(sha256(rawToken))
                .user(user)
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .expiresAt(LocalDateTime.now().plusMinutes(8))
                .usedAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(resetTokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.resetPassword(rawToken, "NewPass99", "NewPass99"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Reset token has already been used");
    }

    // ── Test 8: Reject mismatched passwords ──────────────────────────────────

    @Test
    void resetPassword_mismatchedPasswords_throwsException() {
        assertThatThrownBy(() -> service.resetPassword("any-token", "NewPass99", "Different1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Passwords do not match");

        verifyNoInteractions(resetTokenRepository);
    }

    // ── Test 9: Reset password successfully ──────────────────────────────────

    @Test
    void resetPassword_validToken_resetsPasswordAndReturnsSuccess() {
        String rawToken = "valid-token";
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash(sha256(rawToken))
                .user(user)
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .expiresAt(LocalDateTime.now().plusMinutes(8))
                .build();

        when(resetTokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPass99")).thenReturn("encoded-hash");

        RegisterResponse response = service.resetPassword(rawToken, "NewPass99", "NewPass99");

        verify(userRepository).save(user);
        assertThat(user.getPasswordHash()).isEqualTo("encoded-hash");
        assertThat(response.getMessage()).isEqualTo("Password reset successfully.");
    }

    // ── Test 11: Reset token deleted after use (no reuse) ─────────────────────

    @Test
    void resetPassword_success_deletesToken() {
        String rawToken = "single-use-token";
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash(sha256(rawToken))
                .user(user)
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .expiresAt(LocalDateTime.now().plusMinutes(9))
                .build();

        when(resetTokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        service.resetPassword(rawToken, "NewPass99", "NewPass99");

        verify(resetTokenRepository).delete(token);
    }

    // ── Test 12: Old OTP cannot be reused after verification ──────────────────

    @Test
    void verifyOtp_validOtp_otpDeletedSoCannotBeReused() {
        OTP validOtp = OTP.builder()
                .otpCode("654321")
                .user(user)
                .otpType(OtpType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusMinutes(4))
                .build();

        when(userRepository.findByEmail("ahmed@example.com")).thenReturn(Optional.of(user));
        when(otpRepository.findByUserAndOtpCodeAndOtpType(user, "654321", OtpType.PASSWORD_RESET))
                .thenReturn(Optional.of(validOtp));

        service.verifyOtp("ahmed@example.com", "654321");

        verify(otpRepository).delete(validOtp);

        when(otpRepository.findByUserAndOtpCodeAndOtpType(user, "654321", OtpType.PASSWORD_RESET))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOtp("ahmed@example.com", "654321"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid OTP");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
