package com.project.FitLink.service;

import com.project.FitLink.auth.FitLinkUserDetails;
import com.project.FitLink.dto.Auth.RegisterResponse;
import com.project.FitLink.dto.Auth.TokenResponse;
import com.project.FitLink.entities.users.OTP;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.exception.AppException;
import com.project.FitLink.exception.ErrorCode;
import com.project.FitLink.repository.users.OtpRepository;
import com.project.FitLink.repository.users.UserRepository;
import com.project.FitLink.utils.enums.OtpType;
import com.project.FitLink.utils.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRepository otpRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final jwtService jwtService;

    private static final int COOLDOWN_MINUTES = 2;
    private static final int OTP_EXPIRY_MINUTES = 10;

    @Transactional
    public TokenResponse verifyEmail(String email, String otpCode) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "User not found"));

        OTP otp = findValidOtp(user, otpCode, OtpType.VERIFY);

        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        otpRepository.delete(otp);

        FitLinkUserDetails userDetails = FitLinkUserDetails.builder()
                .id(user.getId())
                .publicId(user.getPublicId())
                .username(user.getUserName())
                .email(user.getEmail())
                .password(null)
                .tokenVersion(user.getTokenVersion())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        return TokenResponse.builder()
                .accessToken(jwtService.generateAccessToken())
                .refreshToken(jwtService.generateRefreshToken())
                .userName(user.getUserName())
                .role("ROLE_USER")
                .build();
    }

    @Transactional
    public RegisterResponse resend(String email, OtpType otpType) {
        UserEntity user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return new RegisterResponse("OTP resent successfully. Please check your email.");
        }

        checkCooldown(user, otpType);
        sendOtp(user, otpType);

        return new RegisterResponse("OTP resent successfully. Please check your email.");
    }

    private void checkCooldown(UserEntity user, OtpType otpType) {
        otpRepository.findTopByUserAndOtpTypeOrderByCreatedAtDesc(user, otpType)
                .ifPresent(last -> {
                    LocalDateTime cooldownEnd = last.getCreatedAt().plusMinutes(COOLDOWN_MINUTES);
                    if (LocalDateTime.now().isBefore(cooldownEnd)) {
                        throw new AppException(
                                ErrorCode.OTP_RESEND_COOLDOWN,
                                "Please wait " + COOLDOWN_MINUTES + " minutes before requesting a new OTP"
                        );
                    }
                });
    }

    void sendOtp(UserEntity user, OtpType otpType) {
        otpRepository.deleteByUserAndOtpType(user, otpType);
        String otpCode = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        otpRepository.save(OTP.builder()
                .otpCode(otpCode)
                .user(user)
                .otpType(otpType)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .build());

        if (otpType == OtpType.PASSWORD_RESET) {
            emailService.sendForgotPasswordOtp(user.getEmail(), user.getUserName(), otpCode);
        } else {
            emailService.sendVerifyEmailOtp(user.getEmail(), user.getUserName(), otpCode);
        }
    }

    private OTP findValidOtp(UserEntity user, String otpCode, OtpType otpType) {
        OTP otp = otpRepository.findByUserAndOtpCodeAndOtpType(user, otpCode, otpType)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_OTP, "Invalid OTP"));
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.OTP_EXPIRED, "OTP has expired");
        }
        return otp;
    }
}
