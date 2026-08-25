package com.project.FitLink.dto.Auth.role;

import com.project.FitLink.utils.enums.gym.WorkingDay;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@Schema(description = "Profile data required when selecting the GYM role")
public class GymProfileRequest {

    @NotBlank(message = "Gym name is required")
    @Size(min = 3, max = 100)
    @Schema(description = "Display name of the gym", example = "Iron Zone Gym")
    private String gymName;

    @Schema(description = "Gym logo image")
    private MultipartFile gymLogo;

    @Schema(description = "Free-form gym type labels (e.g. Fitness, CrossFit, Yoga)")
    private List<String> gymTypes;

    @Schema(description = "Year the gym was established", example = "2015")
    private Integer establishedYear;

    @Size(max = 2000)
    @Schema(description = "Brief description of the gym")
    private String description;

    @Valid
    @NotNull(message = "Location is required")
    @Schema(description = "Physical location of the gym")
    private Location location;

    @Valid
    @NotNull(message = "Working hours are required")
    @Schema(description = "Opening/closing times and working days")
    private WorkingHours workingHours;

    @Schema(description = "List of available facilities (e.g. Parking, Lockers, Showers)")
    private List<String> facilities;

    @Size(max = 255)
    @Schema(description = "Website URL")
    private String websiteUrl;

    @Getter
    @Setter
    public static class Location {
        @Size(max = 500)
        @Schema(description = "Full address string", example = "123 Main St, Cairo, Egypt")
        private String address;

        @Schema(description = "Latitude coordinate", example = "29.9868")
        private Double latitude;

        @Schema(description = "Longitude coordinate", example = "31.3018")
        private Double longitude;
    }

    @Getter
    @Setter
    public static class WorkingHours {
        @Schema(description = "Opening time", example = "09:45")
        private LocalTime openingTime;

        @Schema(description = "Closing time", example = "21:45")
        private LocalTime closingTime;

        @Schema(description = "Working days preset", example = "EVERYDAY")
        private WorkingDay workingDays;
    }
}
