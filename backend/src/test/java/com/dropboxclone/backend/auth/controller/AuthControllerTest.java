package com.dropboxclone.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dropboxclone.backend.auth.dto.AuthResponse;
import com.dropboxclone.backend.auth.dto.AuthResult;
import com.dropboxclone.backend.auth.dto.LoginRequest;
import com.dropboxclone.backend.auth.dto.RegisterRequest;
import com.dropboxclone.backend.auth.security.RefreshTokenService;
import com.dropboxclone.backend.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AuthController authController = new AuthController(authService, refreshTokenService);

    @Test
    void delegatesRegistration() {
        RegisterRequest request = new RegisterRequest("user@example.com", "password123");
        AuthResponse expected = new AuthResponse("token", "Bearer", 3600, "user-1", "user@example.com");
        AuthResult authResult = new AuthResult(expected, "raw_refresh_token");
        when(authService.register(request)).thenReturn(authResult);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthResponse actual = authController.register(request, response);

        assertThat(actual).isEqualTo(expected);
        verify(authService).register(request);
    }

    @Test
    void delegatesLogin() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        AuthResponse expected = new AuthResponse("token", "Bearer", 3600, "user-1", "user@example.com");
        AuthResult authResult = new AuthResult(expected, "raw_refresh_token");
        when(authService.login(request)).thenReturn(authResult);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthResponse actual = authController.login(request, response);

        assertThat(actual).isEqualTo(expected);
        verify(authService).login(request);
    }
}
