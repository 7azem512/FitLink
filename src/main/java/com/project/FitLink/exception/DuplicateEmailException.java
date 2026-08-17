package com.project.FitLink.exception;

public class DuplicateEmailException extends AppException {
    public DuplicateEmailException() {
        super(ErrorCode.DUPLICATE_EMAIL, "Email already exists");
    }
}
