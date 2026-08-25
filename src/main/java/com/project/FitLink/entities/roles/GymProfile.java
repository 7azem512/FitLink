package com.project.FitLink.entities.roles;

import com.project.FitLink.auditing.AuditEntity;
import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.utils.enums.gym.WorkingDay;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "gym_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class GymProfile extends AuditEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Size(min = 3, max = 100)
    @Column(name = "gym_name", nullable = false, length = 100)
    private String gymName;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "gym_types",
            joinColumns = @JoinColumn(name = "gym_profile_id")
    )
    @Column(name = "gym_type", length = 50)
    private Set<String> gymTypes = new HashSet<>();

    @Column(name = "established_year")
    private Integer establishedYear;

    @Lob
    @Column(name = "description")
    private String description;

    @Size(max = 500)
    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "whatsapp", length = 20)
    private String whatsapp;

    @Column(name = "website_url", length = 255)
    private String websiteUrl;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "working_days", length = 30)
    private WorkingDay workingDays;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "gym_facilities",
            joinColumns = @JoinColumn(name = "gym_profile_id")
    )
    @OrderColumn(name = "position")
    @Column(name = "facility", length = 100)
    private List<String> facilities = new ArrayList<>();

    @Column(name = "commercial_registration", length = 100)
    private String commercialRegistration;

    @Column(name = "tax_card", length = 100)
    private String taxCard;

    @Column(name = "owner_id", length = 100)
    private String ownerId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "gym_additional_images",
            joinColumns = @JoinColumn(name = "gym_profile_id")
    )
    @OrderColumn(name = "position")
    @Column(name = "image_url", length = 500)
    private List<String> additionalImages = new ArrayList<>();

    // Inverse side of CoachProfile.currentGym — lets you read gym.getCoaches().
    // The FK (current_gym_id) lives on the coach_profile table; loaded lazily on access.
    @OneToMany(mappedBy = "currentGym", fetch = FetchType.LAZY)
    private List<CoachProfile> coaches = new ArrayList<>();
}
