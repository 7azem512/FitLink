package com.project.FitLink.dto.Auth.refresh;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Request body for refreshing the access token")
public class RefreshRequest {

    @Schema(description = "Valid refresh token obtained from login or verify-otp", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJSRUZSRVNIIn0.fake_refresh_token", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
