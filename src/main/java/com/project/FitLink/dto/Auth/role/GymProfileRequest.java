package com.project.FitLink.dto.Auth.role;

import com.project.FitLink.utils.enums.gym.GymType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Schema(description = "Profile data required when selecting the GYM role")
public class GymProfileRequest {

    @NotBlank(message = "Gym name is required")
    @Size(min = 3, max = 100)
    private String gymName;

    private GymType gymType;

    private Integer establishYear;

    private String description;

    @Size(max = 60)
    private String country;

    @Size(max = 60)
    private String city;

    @Size(max = 80)
    private String area;

    @Size(max = 500)
    private String googleMapsUrl;

    @Size(max = 20)
    private String phoneNumber;

    @Size(max = 20)
    private String whatsapp;

    @Size(max = 255)
    private String websiteUrl;

    private LocalTime openingTime;

    private LocalTime closingTime;

    private Set<DayOfWeek> workingDays;

    private List<String> facilities;

    @Size(max = 100)
    private String commercialRegistration;

    @Size(max = 100)
    private String taxCard;

    @Size(max = 100)
    private String ownerId;
}
