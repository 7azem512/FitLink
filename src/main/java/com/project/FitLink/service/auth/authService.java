package com.project.FitLink.service.auth;

import com.project.FitLink.auth.FitLinkUserDetails;
import com.project.FitLink.dto.Auth.login.LoginRequest;
import com.project.FitLink.dto.Auth.login.TokenResponse;
import com.project.FitLink.dto.Auth.refresh.RefreshResponse;
import com.project.FitLink.dto.Auth.register.RegisterResponse;
import com.project.FitLink.dto.Auth.role.CoachProfileRequest;
import com.project.FitLink.dto.Auth.role.GymProfileRequest;
import com.project.FitLink.dto.Auth.role.SelectRoleRequest;
import com.project.FitLink.dto.Auth.role.SelectRoleResponse;
import com.project.FitLink.dto.Auth.role.TraineeProfileRequest;
import com.project.FitLink.entities.roles.CoachProfile;
import com.project.FitLink.entities.roles.GymProfile;
import com.project.FitLink.entities.roles.TraineeProfile;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.entities.users.Role;
import com.project.FitLink.entities.users.UserRole;
import com.project.FitLink.exception.AppException;
import com.project.FitLink.exception.ErrorCode;
import com.project.FitLink.repository.roles.CoachProfileRepository;
import com.project.FitLink.repository.roles.GymProfileRepository;
import com.project.FitLink.repository.roles.TraineeProfileRepository;
import com.project.FitLink.repository.users.RoleRepository;
import com.project.FitLink.repository.users.UserRepository;
import com.project.FitLink.repository.users.UserRoleRepository;
import com.project.FitLink.service.fileStorage.StorageService;
import com.project.FitLink.utils.enums.storage.StorageFolder;
import com.project.FitLink.utils.enums.user.Roles;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class authService {

    private final jwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final GymProfileRepository gymProfileRepository;
    private final TraineeProfileRepository traineeProfileRepository;
    private final StorageService storageService;

    /**
     * Authenticates a user and generates access and refresh tokens.
     * @param loginRequest The login request containing email and password.
     * @return A TokenResponse containing the access and refresh tokens.
     * @throws AppException If authentication fails due to invalid credentials.
     */
    //TODO: Refactor this (reduce second reading query and related get queries)
    public TokenResponse loginProcess(LoginRequest loginRequest) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(), loginRequest.getPassword()
        );
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(authentication);
        } catch (BadCredentialsException | UsernameNotFoundException exception) {
            log.warn("Login failed", exception);
            throw new AppException(ErrorCode.BAD_CREDENTIALS, "Invalid email or password");
        }

        if(auth == null || !auth.isAuthenticated()) {
            log.warn("Login failed");
            throw new AppException(ErrorCode.BAD_CREDENTIALS, "Invalid email or password");
        }

        UserEntity user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_CREDENTIALS, "Invalid email or password"));
        if (!user.isEmailVerified()) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED, "Email not verified. Please verify your email first");
        }

        SecurityContextHolder.getContext().setAuthentication(auth);
        //TODO: use the methode that make the token from the principal instead of the user
        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        log.info("User {} logged in successfully", loginRequest.getEmail());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userName(user.getUserName())
                .role(user.getRoles().stream()
                        .map(ur -> "ROLE_" + ur.getRole().getRoleCode().name())
                        .findFirst().orElse(""))
                .build();
    }

    public RegisterResponse logout() {
        // TODO: Implement server-side logout by revoking the Refresh Token session in Redis.
        //
        // TEMPORARY BEHAVIOR — this endpoint does NOT revoke tokens on the server.
        // Existing Access Tokens remain valid until expiration.
        // Existing Refresh Tokens remain valid until expiration.
        // The mobile client MUST delete both tokens from local storage immediately.
        // Real server-side logout will be completed by deleting the Refresh Token session from Redis.

        SecurityContextHolder.clearContext();

        log.info("User logged out (local context cleared). Token revocation pending Redis implementation.");

        return new RegisterResponse("Logged out locally. Server-side refresh token revocation will be implemented with Redis.");
    }

    /**
     * Refreshes the access token using a valid refresh token.
     * @param refreshToken The refresh token to use for refreshing the access token.
     * @return A RefreshResponse containing the new access token.
     * @throws AppException If the refresh token is invalid or expired.
     */
    public RefreshResponse refreshToken(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.extractClaims(refreshToken);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN, "Expired token, please login again");
        }

        if (!"REFRESH Token".equals(claims.getSubject())) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid token type");
        }

        String publicIdStr = claims.get("publicId", String.class);
        if (publicIdStr == null) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid token structure");
        }
        UserEntity user = userRepository.findByPublicId(UUID.fromString(publicIdStr))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN, "User not found, please login again"));

        String newAccessToken = jwtService.generateAccessToken(user);

        log.info("Access token refreshed for user: {}", user.getEmail());

        return RefreshResponse.builder()
                .newAccessToken(newAccessToken)
                .build();
    }

    @Transactional
    public SelectRoleResponse assignRole(SelectRoleRequest selectRoleRequest) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof FitLinkUserDetails currentUser)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Invalid authentication. User details not found.");
        }

        UserEntity user = userRepository.findByPublicId(currentUser.getPublicId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "User not found"));

        Roles newRole = parseSelectableRole(selectRoleRequest.getRole());

        // Inspect the user's roles in a single pass:
        // - an existing UNASSIGNED role means assignment is allowed;
        // - holding the requested role already means there is nothing to assign.
        UserRole unassignedRole = null;
        boolean hasRequestedRole = false;
        for (UserRole userRole : user.getRoles()) {
            Roles roleCode = userRole.getRole().getRoleCode();
            if (roleCode == Roles.UNASSIGNED) {
                unassignedRole = userRole;
            } else if (roleCode == newRole) {
                hasRequestedRole = true;
            }
        }

        // The user already holds the requested role -> nothing to do.
        if (hasRequestedRole) {
            throw new AppException(ErrorCode.ROLE_ALREADY_ASSIGNED, "You already have this role. Try to login again");
        }

        // Create the role-specific profile (uploads any files sent in the request).
        List<String> uploadedUrls = new ArrayList<>();
        try {
            switch (newRole) {
                case TRAINEE -> uploadedUrls.addAll(createTraineeProfile(user, selectRoleRequest));
                case GYM -> uploadedUrls.addAll(createGymProfile(user, selectRoleRequest));
                case COACH -> uploadedUrls.addAll(createCoachProfile(user, selectRoleRequest));
                default -> throw new AppException(ErrorCode.INVALID_ROLE, "Invalid role specified. Allowed values are TRAINEE, COACH, GYM.");
            }
        } catch (RuntimeException e) {
            // Compensate: remove objects already stored so no orphaned files are left in storage.
            uploadedUrls.forEach(storageService::deleteByUrl);
            throw e;
        }

        // A profile was created, so the UNASSIGNED role is no longer needed.
        if (unassignedRole != null) {
            user.getRoles().remove(unassignedRole);
            userRoleRepository.delete(unassignedRole);
        }

        // Grant the requested role.
        Role newRoleEntity = roleRepository.findByRoleCode(newRole)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_ROLE, "Role not found in database."));
        UserRole newUserRole = UserRole.builder()
                .user(user)
                .role(newRoleEntity)
                .build();
        user.getRoles().add(newUserRole);
        userRoleRepository.save(newUserRole);

        String newAccessToken  = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        log.info("User {} selected role: {}", user.getEmail(), newRole);

        return SelectRoleResponse.builder()
                .role(newRole.name())
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .message("Role updated successfully.")
                .build();
    }

    private Roles parseSelectableRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            throw new AppException(ErrorCode.INVALID_ROLE, "Invalid role specified. Allowed values are TRAINEE, COACH, GYM.");
        }

        Roles role;
        try {
            role = Roles.valueOf(rawRole.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_ROLE, "Invalid role specified. Allowed values are TRAINEE, COACH, GYM.");
        }

        if (role == Roles.ADMIN || role == Roles.SYSTEM || role == Roles.UNASSIGNED || role == Roles.USER) {
            throw new AppException(ErrorCode.ROLE_NOT_ALLOWED, "Cannot select this role.");
        }
        return role;
    }

    /**
     * Creates the coach profile, uploading the CV and intro video (if sent) to storage.
     *
     * @return the URLs of the objects uploaded by this call (used for rollback on failure)
     */
    private List<String> createCoachProfile(UserEntity user, SelectRoleRequest request) {
        CoachProfileRequest dto = request.getCoachProfile();
        if (dto == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Coach profile data is required");
        }

        List<String> uploadedUrls = new ArrayList<>();

        GymProfile currentGym = null;
        if (dto.getCurrentGymId() != null) {
            currentGym = gymProfileRepository.findById(dto.getCurrentGymId())
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Gym not found"));
        }

        String cvUrl = uploadIfPresent(dto.getCv(), StorageFolder.COACH_CV, uploadedUrls);
        String introVideoUrl = uploadIfPresent(dto.getIntroVideo(), StorageFolder.COACH_INTRO_VIDEO, uploadedUrls);

        CoachProfile profile = CoachProfile.builder()
                .id(user.getPublicId())
                .user(user)
                .nationality(dto.getNationality())
                .city(dto.getCity())
                .gender(dto.getGender())
                .heightCm(dto.getHeightCm())
                .weightKg(dto.getWeightKg())
                .birthday(dto.getBirthday())
                .yearsOfExperience(dto.getYearsOfExperience())
                .languageSpoken(dto.getLanguageSpoken())
                .currentGym(currentGym)
                .specializations(dto.getSpecializations() != null
                        ? new HashSet<>(dto.getSpecializations())
                        : new HashSet<>())
                .certifications(dto.getCertifications() != null
                        ? new ArrayList<>(dto.getCertifications())
                        : new ArrayList<>())
                .bio(dto.getBio())
                .cvUrl(cvUrl)
                .introVideoUrl(introVideoUrl)
                .build();
        coachProfileRepository.save(profile);

        log.info("Coach profile created for user: {}", user.getEmail());
        return uploadedUrls;
    }

    /**
     * Creates the gym profile, uploading the logo, cover and gallery images (if sent) to storage.
     *
     * @return the URLs of the objects uploaded by this call (used for rollback on failure)
     */
    private List<String> createGymProfile(UserEntity user, SelectRoleRequest request) {
        GymProfileRequest dto = request.getGymProfile();
        if (dto == null || dto.getGymName() == null || dto.getGymName().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Gym profile data is required");
        }

        List<String> uploadedUrls = new ArrayList<>();
        String logoUrl = uploadIfPresent(dto.getLogo(), StorageFolder.GYM_LOGO, uploadedUrls);
        String coverUrl = uploadIfPresent(dto.getCover(), StorageFolder.GYM_COVER, uploadedUrls);
        List<String> galleryUrls = storageService.uploadAll(dto.getGallery(), StorageFolder.GYM_GALLERY);
        uploadedUrls.addAll(galleryUrls);

        GymProfile profile = GymProfile.builder()
                .id(user.getPublicId())
                .user(user)
                .logoUrl(logoUrl)
                .gymName(dto.getGymName())
                .coverImageUrl(coverUrl)
                .gymType(dto.getGymType())
                .establishYear(dto.getEstablishYear())
                .description(dto.getDescription())
                .country(dto.getCountry())
                .city(dto.getCity())
                .area(dto.getArea())
                .googleMapsUrl(dto.getGoogleMapsUrl())
                .phoneNumber(dto.getPhoneNumber())
                .whatsapp(dto.getWhatsapp())
                .websiteUrl(dto.getWebsiteUrl())
                .openingTime(dto.getOpeningTime())
                .closingTime(dto.getClosingTime())
                .workingDays(dto.getWorkingDays() != null
                        ? new HashSet<>(dto.getWorkingDays())
                        : new HashSet<>())
                .facilities(dto.getFacilities() != null
                        ? new ArrayList<>(dto.getFacilities())
                        : new ArrayList<>())
                .additionalImages(new ArrayList<>(galleryUrls))
                .commercialRegistration(dto.getCommercialRegistration())
                .taxCard(dto.getTaxCard())
                .ownerId(dto.getOwnerId())
                .build();
        gymProfileRepository.save(profile);

        log.info("Gym profile created for user: {}", user.getEmail());
        return uploadedUrls;
    }

    /**
     * Creates the trainee profile, uploading the avatar (if sent) to storage.
     *
     * @return the URLs of the objects uploaded by this call (used for rollback on failure)
     */
    private List<String> createTraineeProfile(UserEntity user, SelectRoleRequest request) {
        TraineeProfileRequest dto = request.getTraineeProfile();
        if (dto == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Trainee profile data is required");
        }

        List<String> uploadedUrls = new ArrayList<>();
        String avatarUrl = uploadIfPresent(dto.getAvatar(), StorageFolder.TRAINEE_AVATAR, uploadedUrls);

        TraineeProfile profile = TraineeProfile.builder()
                .id(user.getPublicId())
                .user(user)
                .profileImageUrl(avatarUrl)
                .gender(dto.getGender())
                .heightCm(dto.getHeightCm())
                .weightKg(dto.getWeightKg())
                .birthday(dto.getBirthday())
                .goal(dto.getGoal())
                .activityLevel(dto.getActivityLevel())
                .workingFrequency(dto.getWorkingFrequency())
                .preferredTraining(dto.getPreferredTraining())
                .preferredWorkoutTime(dto.getPreferredWorkoutTime())
                .location(dto.getLocation())
                .build();
        traineeProfileRepository.save(profile);

        log.info("Trainee profile created for user: {}", user.getEmail());
        return uploadedUrls;
    }

    /**
     * Uploads a file only when it was actually provided; returns the public URL or null.
     * Tracks every uploaded URL so {@code assignRole} can remove them if the transaction fails.
     */
    private String uploadIfPresent(MultipartFile file, StorageFolder folder, List<String> uploadedUrls) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String url = storageService.upload(file, folder);
        uploadedUrls.add(url);
        return url;
    }
}
