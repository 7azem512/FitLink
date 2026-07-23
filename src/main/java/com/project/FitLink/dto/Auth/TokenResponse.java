package com.project.FitLink.dto.Auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Response containing JWT access and refresh tokens")
public class TokenResponse {

    @Schema(description = "Short-lived JWT access token. Include in Authorization header as: Bearer <token>", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJBQ0NFU1MifQ.fake_access_token")
    private String accessToken;

    @Schema(description = "Long-lived JWT refresh token. Use to obtain a new access token.", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJSRUZSRVNIIn0.fake_refresh_token")
    private String refreshToken;

    @Schema(description = "Display name of the authenticated user", example = "John Doe")
    private String userName;

    @Schema(description = "Role assigned to the user", example = "ROLE_USER")
    private String role;
}
