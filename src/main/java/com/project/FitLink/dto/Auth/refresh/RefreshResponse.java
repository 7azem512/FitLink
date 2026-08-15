package com.project.FitLink.dto.Auth.refresh;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Response containing a new access token")
public class RefreshResponse {

    @Schema(description = "New short-lived JWT access token", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJBQ0NFU1MifQ.fake_new_access_token")
    private String newAccessToken;
}
