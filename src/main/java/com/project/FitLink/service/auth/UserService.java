package com.project.FitLink.service.auth;

import com.project.FitLink.dto.Auth.RegisterRequest;
import com.project.FitLink.dto.Auth.RegisterResponse;
import com.project.FitLink.entities.users.Role;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.entities.users.UserRole;
import com.project.FitLink.exception.AppException;
import com.project.FitLink.exception.ErrorCode;
import com.project.FitLink.repository.users.RoleRepository;
import com.project.FitLink.repository.users.UserRepository;
import com.project.FitLink.repository.users.UserRoleRepository;
import com.project.FitLink.utils.enums.auth.AuthProvider;
import com.project.FitLink.utils.enums.auth.OtpType;
import com.project.FitLink.utils.enums.user.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.project.FitLink.utils.enums.auth.AuthProvider.LOCAL;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final OtpService otpService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.DUPLICATE_EMAIL, "Email already exists");
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH, "Passwords do not match");
        }

        UserEntity user = UserEntity.builder()
                .userName(request.getUserName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .emailVerified(false)
                .provider(LOCAL)
                .build();
        userRepository.save(user);

        // Assign UNASSIGNED default role to new user
        Role unassignedRoleEntity = roleRepository.findByRoleCode(Roles.UNASSIGNED)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_ROLE, "Default role not found"));
        UserRole unassignedRole = UserRole.builder()
                .user(user)
                .role(unassignedRoleEntity)
                .build();
        userRoleRepository.save(unassignedRole);

        otpService.sendOtp(user, OtpType.VERIFY);

        return new RegisterResponse("Registration successful. Please check your email for the OTP.");
    }

}
