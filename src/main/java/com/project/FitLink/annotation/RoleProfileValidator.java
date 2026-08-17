package com.project.FitLink.annotation;

import com.project.FitLink.dto.Auth.role.SelectRoleRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RoleProfileValidator
        implements ConstraintValidator<ValidRoleProfile, SelectRoleRequest> {

    @Override
    public boolean isValid(
            SelectRoleRequest request,
            ConstraintValidatorContext context
    ) {

        if (request == null || request.getRole() == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        return switch (request.getRole()) {

            case "TRAINEE" -> validateTrainee(request, context);

            case "COACH" -> validateCoach(request, context);

            case "GYM" -> validateGym(request, context);

            default -> true;
        };
    }

    private boolean validateTrainee(
            SelectRoleRequest request,
            ConstraintValidatorContext context
    ) {

        boolean valid = true;

        if (request.getTraineeProfile() == null) {
            addViolation(
                    context,
                    "Trainee profile is required when role is TRAINEE",
                    "traineeProfile"
            );

            valid = false;
        }

        if (request.getCoachProfile() != null) {
            addViolation(
                    context,
                    "Coach profile must not be sent when role is TRAINEE",
                    "coachProfile"
            );

            valid = false;
        }

        if (request.getGymProfile() != null) {
            addViolation(
                    context,
                    "Gym profile must not be sent when role is TRAINEE",
                    "gymProfile"
            );

            valid = false;
        }

        return valid;
    }

    private boolean validateCoach(
            SelectRoleRequest request,
            ConstraintValidatorContext context
    ) {

        boolean valid = true;

        if (request.getCoachProfile() == null) {
            addViolation(
                    context,
                    "Coach profile is required when role is COACH",
                    "coachProfile"
            );

            valid = false;
        }

        if (request.getTraineeProfile() != null) {
            addViolation(
                    context,
                    "Trainee profile must not be sent when role is COACH",
                    "traineeProfile"
            );

            valid = false;
        }

        if (request.getGymProfile() != null) {
            addViolation(
                    context,
                    "Gym profile must not be sent when role is COACH",
                    "gymProfile"
            );

            valid = false;
        }

        return valid;
    }

    private boolean validateGym(
            SelectRoleRequest request,
            ConstraintValidatorContext context
    ) {

        boolean valid = true;

        if (request.getGymProfile() == null) {
            addViolation(
                    context,
                    "Gym profile is required when role is GYM",
                    "gymProfile"
            );

            valid = false;
        }

        if (request.getTraineeProfile() != null) {
            addViolation(
                    context,
                    "Trainee profile must not be sent when role is GYM",
                    "traineeProfile"
            );

            valid = false;
        }

        if (request.getCoachProfile() != null) {
            addViolation(
                    context,
                    "Coach profile must not be sent when role is GYM",
                    "coachProfile"
            );

            valid = false;
        }

        return valid;
    }

    private void addViolation(
            ConstraintValidatorContext context,
            String message,
            String field
    ) {

        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}