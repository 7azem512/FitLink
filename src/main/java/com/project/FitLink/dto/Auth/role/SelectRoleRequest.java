package com.project.FitLink.dto.Auth.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SelectRoleRequest {

    @NotBlank(message = "Role cannot be blank")
    @Pattern(regexp = "TRAINEE|COACH|GYM", message = "Invalid role. Allowed values are TRAINEE, COACH, GYM.")
    @Schema(description = "The role to assign to the user", example = "COACH", allowableValues = {"TRAINEE", "COACH", "GYM"})
    private String role;

    @Schema(description = "Coach profile data. Required when role = COACH.")
    private CoachProfileRequest coachProfile;

    @Schema(description = "Gym profile data. Required when role = GYM.")
    private GymProfileRequest gymProfile;

    @Schema(description = "Trainee profile data. Required when role = TRAINEE.")
    private TraineeProfileRequest traineeProfile;
}
