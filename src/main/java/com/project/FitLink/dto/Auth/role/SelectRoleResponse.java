package com.project.FitLink.dto.Auth.role;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SelectRoleResponse {
    @Schema(description = "The role selected by the user", example = "COACH")
    private String role;

    @Schema(description = "New JWT access token", example = "eyJ...new_access_token")
    private String accessToken;

    @Schema(description = "New JWT refresh token", example = "eyJ...new_refresh_token")
    private String refreshToken;

    @Schema(description = "A message indicating the success of the role selection", example = "Role selected successfully.")
    private String message;
}
