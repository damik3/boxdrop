package com.dropboxclone.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dropboxclone.backend.auth.dto.AuthResponse;
import com.dropboxclone.backend.auth.dto.LoginRequest;
import com.dropboxclone.backend.auth.dto.RegisterRequest;
import com.dropboxclone.backend.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final AuthController authController = new AuthController(authService);

    @Test
    void delegatesRegistration() {
        RegisterRequest request = new RegisterRequest("user@example.com", "password123");
        AuthResponse response = new AuthResponse("token", "Bearer", 3600, "user-1", "user@example.com");
        when(authService.register(request)).thenReturn(response);

        AuthResponse actual = authController.register(request);

        assertThat(actual).isEqualTo(response);
        verify(authService).register(request);
    }

    @Test
    void delegatesLogin() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        AuthResponse response = new AuthResponse("token", "Bearer", 3600, "user-1", "user@example.com");
        when(authService.login(request)).thenReturn(response);

        AuthResponse actual = authController.login(request);

        assertThat(actual).isEqualTo(response);
        verify(authService).login(request);
    }
}
