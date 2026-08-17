package com.project.FitLink.dto.Auth.login;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Request body for Sign in with Google")
public class GoogleLoginRequest {

    @Schema(description = "Google ID token obtained from Google Sign-In on the mobile client. Sent to the backend only once and never used as the application token.", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6InNvbWUua2V5LmlkIn0.payload.signature", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Google ID token is required")
    private String idToken;
}
