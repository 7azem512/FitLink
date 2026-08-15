package com.project.FitLink.dto.Auth.password;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for resetting the password using a valid reset token")
public class ResetPasswordRequest {

    @Schema(description = "Single-use reset token received from /forget-password/verify-otp", example = "a3f8c2d1e9b74f6a8c2d1e9b74f6a8c2d1e9b74f6a8c2d1e9b74f6a8c2d1e9b", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Reset token is required")
    private String resetToken;

    @Schema(description = "New password. Must be 8-16 characters and contain at least one letter and one number.", example = "NewPass99", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 16, accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 16, message = "Password must be between 8 and 16 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain at least one letter and one number")
    private String newPassword;

    @Schema(description = "Must match newPassword exactly", example = "NewPass99", requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;
}
