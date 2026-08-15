package com.project.FitLink.entities.roles;

import com.project.FitLink.auditing.AuditEntity;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.utils.enums.trainee.ActivityLevel;
import com.project.FitLink.utils.enums.trainee.PreferredTraining;
import com.project.FitLink.utils.enums.trainee.TraineeGoal;
import com.project.FitLink.utils.enums.trainee.WorkingFrequency;
import com.project.FitLink.utils.enums.trainee.WorkoutTime;
import com.project.FitLink.utils.enums.user.Gender;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "trainee_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TraineeProfile extends AuditEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal", length = 30)
    private TraineeGoal goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", length = 20)
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "working_frequency", length = 20)
    private WorkingFrequency workingFrequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_training", length = 30)
    private PreferredTraining preferredTraining;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_workout_time", length = 20)
    private WorkoutTime preferredWorkoutTime;

    @Column(name = "location", length = 120)
    private String location;
}
