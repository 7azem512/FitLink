package com.project.FitLink.dto.Auth.role;

import com.project.FitLink.utils.enums.trainee.ActivityLevel;
import com.project.FitLink.utils.enums.trainee.PreferredTraining;
import com.project.FitLink.utils.enums.trainee.TraineeGoal;
import com.project.FitLink.utils.enums.trainee.WorkingFrequency;
import com.project.FitLink.utils.enums.trainee.WorkoutTime;
import com.project.FitLink.utils.enums.user.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Profile data required when selecting the TRAINEE role")
public class TraineeProfileRequest {

    // Public URL returned by POST /storage/upload for StorageFolder.TRAINEE_AVATAR.
    @Size(max = 500, message = "Profile image URL must not exceed 500 characters")
    private String profileImageUrl;

    private Gender gender;

    private Double heightCm;

    private Double weightKg;

    @Past(message = "Birthday must be in the past")
    private LocalDate birthday;

    private TraineeGoal goal;

    private ActivityLevel activityLevel;

    private WorkingFrequency workingFrequency;

    private PreferredTraining preferredTraining;

    private WorkoutTime preferredWorkoutTime;

    @Size(max = 120)
    private String location;
}
