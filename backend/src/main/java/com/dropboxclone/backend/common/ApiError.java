package com.dropboxclone.backend.common;

import org.springframework.http.HttpStatus;

public enum ApiError {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "No account exists for that email."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Your account could not be found."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "That file could not be found."),
    NOT_ALLOWED(HttpStatus.FORBIDDEN, "You don't have access to this file."),
    FILE_NOT_UPLOADED(HttpStatus.BAD_REQUEST, "This file is not ready to share or download yet."),
    INVALID_FILE_SIZE(HttpStatus.BAD_REQUEST, "That file is too large. Maximum size is 50 MB."),
    UNSUPPORTED_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "Only PNG, JPEG, and PDF files are allowed."),
    MISSING_STORAGE_KEY(HttpStatus.BAD_REQUEST, "This file isn't available right now."),
    CANNOT_SHARE_WITH_SELF(HttpStatus.BAD_REQUEST, "You already own this file."),
    EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "Enter an email address."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with that email already exists."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Please sign in again."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Check the form and try again."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Try again.");

    private final HttpStatus status;
    private final String message;

    ApiError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return name();
    }

    public String message() {
        return message;
    }

    public ApiException exception() {
        return new ApiException(this);
    }

    public ApiErrorResponse toResponse() {
        return new ApiErrorResponse(code(), message());
    }
}
