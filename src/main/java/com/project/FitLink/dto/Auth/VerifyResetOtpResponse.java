package com.project.FitLink.dto.Auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response returned after successful OTP verification, containing a single-use password reset token")
public class VerifyResetOtpResponse {

    @Schema(description = "Temporary single-use token required to call /forget-password/reset", example = "a3f8c2d1e9b74f6a8c2d1e9b74f6a8c2d1e9b74f6a8c2d1e9b74f6a8c2d1e9b")
    private String resetToken;

    @Schema(description = "Token validity in seconds", example = "600")
    private int expiresIn;
}
