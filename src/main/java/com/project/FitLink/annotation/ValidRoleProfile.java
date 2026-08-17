package com.project.FitLink.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RoleProfileValidator.class)
public @interface ValidRoleProfile {

    String message() default "Invalid profile for selected role";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}