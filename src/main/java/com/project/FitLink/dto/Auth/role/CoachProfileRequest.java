package com.project.FitLink.dto.Auth.role;

import com.project.FitLink.utils.enums.coach.CoachSpecialization;
import com.project.FitLink.utils.enums.user.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Profile data required when selecting the COACH role")
public class CoachProfileRequest {

    @Size(max = 60)
    private String nationality;

    @Size(max = 60)
    private String city;

    private Gender gender;

    private Double heightCm;

    private Double weightKg;

    @Past(message = "Birthday must be in the past")
    private LocalDate birthday;

    private Integer yearsOfExperience;

    @Size(max = 50)
    private String languageSpoken;

    private UUID currentGymId;

    private Set<CoachSpecialization> specializations;

    private List<String> certifications;

    private String bio;

    // Uploaded as file parts within the same multipart request; stored under COACH_CV / COACH_INTRO_VIDEO.
    private MultipartFile cv;

    private MultipartFile introVideo;
}
