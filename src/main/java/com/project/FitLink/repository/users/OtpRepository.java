package com.project.FitLink.repository.users;

import com.project.FitLink.entities.users.OTP;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.utils.enums.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<OTP, Long> {
    void deleteByExpiresAtBefore(LocalDateTime now);
    void deleteByUserAndOtpType(UserEntity user, OtpType otpType);
    Optional<OTP> findTopByUserAndOtpTypeOrderByCreatedAtDesc(UserEntity user, OtpType otpType);
    Optional<OTP> findByUserAndOtpCodeAndOtpType(UserEntity user, String otpCode, OtpType otpType);
}
