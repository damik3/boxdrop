package com.dropboxclone.backend.common;

import org.springframework.web.server.ResponseStatusException;

public class ApiException extends ResponseStatusException {
    private final String code;

    public ApiException(ApiError error) {
        super(error.status(), error.message());
        this.code = error.code();
    }

    public String getCode() {
        return code;
    }
}
