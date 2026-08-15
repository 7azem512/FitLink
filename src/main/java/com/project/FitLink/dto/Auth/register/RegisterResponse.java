package com.project.FitLink.dto.Auth.register;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Generic message response")
public class RegisterResponse {

    @Schema(description = "Human-readable result message", example = "Registration successful. Please check your email for the OTP.")
    private String message;
}
