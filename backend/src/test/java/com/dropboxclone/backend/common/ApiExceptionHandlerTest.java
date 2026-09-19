package com.dropboxclone.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsApiExceptionToCodeAndMessage() {
        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ApiError.USER_NOT_FOUND.exception());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(
                new ApiErrorResponse("USER_NOT_FOUND", "No account exists for that email.")
        );
    }

    @Test
    void mapsBadCredentialsToInvalidCredentials() {
        ResponseEntity<ApiErrorResponse> response = handler.handleBadCredentials(
                new BadCredentialsException("Invalid email or password")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(ApiError.INVALID_CREDENTIALS.toResponse());
    }

    @Test
    void mapsUnexpectedErrorsToGenericInternalMessage() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(ApiError.INTERNAL_ERROR.toResponse());
        assertThat(response.getBody().message()).doesNotContain("boom");
    }

    @Test
    void mapsLeftoverResponseStatusExceptionWithoutLeakingNull() {
        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check the form and try again.")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Check the form and try again.");
    }
}
