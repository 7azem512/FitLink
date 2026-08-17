package com.project.FitLink.entities.roles;

import com.project.FitLink.auditing.AuditEntity;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.utils.enums.coach.CoachSpecialization;
import com.project.FitLink.utils.enums.user.Gender;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "coach_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CoachProfile extends AuditEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "nationality", length = 60)
    private String nationality;

    @Column(name = "city", length = 60)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "language_spoken", length = 50)
    private String languageSpoken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_gym_id")
    private GymProfile currentGym;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "coach_specializations",
            joinColumns = @JoinColumn(name = "coach_profile_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "specialization", length = 40)
    private Set<CoachSpecialization> specializations = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "coach_certifications",
            joinColumns = @JoinColumn(name = "coach_profile_id")
    )
    @OrderColumn(name = "position")
    @Column(name = "certification", length = 150)
    private List<String> certifications = new ArrayList<>();

    @Lob
    @Column(name = "bio")
    private String bio;

    @Column(name = "cv_url", length = 500)
    private String cvUrl;

    @Column(name = "intro_video_url", length = 500)
    private String introVideoUrl;
}
