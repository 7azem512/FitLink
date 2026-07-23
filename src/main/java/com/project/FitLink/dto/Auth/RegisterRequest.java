package com.project.FitLink.dto.Auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Request body for user registration")
public class RegisterRequest {

    @Schema(description = "Display name of the user", example = "John Doe", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 3, maxLength = 50)
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String userName;

    @Schema(description = "User's email address. Must be unique.", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email is too long")
    private String email;

    @Schema(description = "Password. Must be 8-16 characters and contain at least one letter and one number.", example = "Pass1234", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 16)
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 16, message = "Password must be between 8 and 16 characters")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "Password must contain at least one letter and one number"
    )
    private String password;

    @Schema(description = "Must match the password field exactly.", example = "Pass1234", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    @Schema(description = "User's phone number. Optional.", example = "01012345678", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String phone;
}
