package com.project.FitLink.service;

import com.project.FitLink.repository.users.OtpRepository;
import com.project.FitLink.repository.users.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private final OtpRepository otpRepository;
    private final PasswordResetTokenRepository resetTokenRepository;

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void removeExpiredOtps() {
        otpRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.debug("Expired OTPs cleaned up");
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void removeExpiredResetTokens() {
        resetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.debug("Expired reset tokens cleaned up");
    }
}
