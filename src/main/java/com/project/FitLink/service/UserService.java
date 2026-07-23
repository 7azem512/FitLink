package com.project.FitLink.service;

import com.project.FitLink.dto.Auth.RegisterRequest;
import com.project.FitLink.dto.Auth.RegisterResponse;
import com.project.FitLink.entities.users.OTP;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.entities.users.UserRole;
import com.project.FitLink.exception.exceptions.DuplicateEmailException;
import com.project.FitLink.repository.users.OtpRepository;
import com.project.FitLink.repository.users.UserRepository;
import com.project.FitLink.repository.users.UserRoleRepository;
import com.project.FitLink.utils.enums.OtpType;
import com.project.FitLink.utils.enums.Roles;
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
    private final UserRoleRepository userRoleRepository;
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

        // Assign UNASSIGNED default role to new user
        UserRole unassignedRole = UserRole.builder()
                .user(user)
                .roleCode(Roles.UNASSIGNED)
                .build();
        userRoleRepository.save(unassignedRole);

        String otpCode = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        otpRepository.save(OTP.builder()
                .otpCode(otpCode)
                .user(user)
                .otpType(OtpType.DEFAULT)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build());

        emailService.sendVerifyEmailOtp(request.getEmail(), request.getUserName(), otpCode);

        return new RegisterResponse("Registration successful. Please check your email for the OTP.");
    }
}
