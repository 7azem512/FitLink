package com.project.FitLink.exception.exceptions;

import com.project.FitLink.exception.AppException;
import com.project.FitLink.exception.ErrorCode;

public class DuplicateEmailException extends AppException {
    public DuplicateEmailException() {
        super(ErrorCode.DUPLICATE_EMAIL, "Email already exists");
    }
}
