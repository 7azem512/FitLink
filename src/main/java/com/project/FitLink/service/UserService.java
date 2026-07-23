package com.project.FitLink.service;

import com.project.FitLink.dto.Auth.RegisterRequest;
import com.project.FitLink.dto.Auth.RegisterResponse;
import com.project.FitLink.entities.users.OTP;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.exception.exceptions.DuplicateEmailException;
import com.project.FitLink.repository.users.OtpRepository;
import com.project.FitLink.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException();
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        UserEntity user = UserEntity.builder()
                .userName(request.getUserName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .emailVerified(false)
                .build();
        userRepository.save(user);

        String otpCode = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        otpRepository.save(OTP.builder()
                .otpCode(otpCode)
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build());

        emailService.sendOtpEmail(request.getEmail(), request.getUserName(), otpCode);

        return new RegisterResponse("Registration successful. Please check your email for the OTP.");
    }
}
