package com.project.FitLink.controller;

import com.project.FitLink.dto.GlobalResponse;
import com.project.FitLink.service.auth.authService;
import com.project.FitLink.utils.enums.user.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
@Tag(name = "Profiles", description = "(Under Development)")
public class ProfileController {

    private final authService authService;

    @Operation(summary = "Delete own profile",
            description = "Deletes the authenticated user's profile for the given role, including all uploaded files (S3) and the role assignment. If deleting a GYM profile, linked coaches are unlinked first.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/me")
    public ResponseEntity<Map<String, Object>> deleteProfile(
            @Parameter(description = "Role of the profile to delete", required = true, example = "GYM")
            @RequestParam Roles role) {
        authService.deleteProfile(role);
        GlobalResponse response = new GlobalResponse();
        response.addMessage("message", role.name() + " profile deleted successfully");
        return ResponseEntity.ok(response.getApiResponse());
    }
}
