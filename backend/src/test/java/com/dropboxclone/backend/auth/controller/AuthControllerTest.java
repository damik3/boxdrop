package com.dropboxclone.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dropboxclone.backend.auth.dto.AuthResponse;
import com.dropboxclone.backend.auth.dto.AuthResult;
import com.dropboxclone.backend.auth.dto.LoginRequest;
import com.dropboxclone.backend.auth.dto.RegisterRequest;
import com.dropboxclone.backend.auth.security.RefreshTokenService;
import com.dropboxclone.backend.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
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
        when(refreshTokenService.getTtlDays()).thenReturn(30L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthResponse actual = authController.register(request, response);

        assertThat(actual).isEqualTo(expected);
        verify(authService).register(request);
        assertRefreshCookieSet(response, "raw_refresh_token");
    }

    @Test
    void delegatesLogin() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        AuthResponse expected = new AuthResponse("token", "Bearer", 3600, "user-1", "user@example.com");
        AuthResult authResult = new AuthResult(expected, "raw_refresh_token");
        when(authService.login(request)).thenReturn(authResult);
        when(refreshTokenService.getTtlDays()).thenReturn(30L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthResponse actual = authController.login(request, response);

        assertThat(actual).isEqualTo(expected);
        verify(authService).login(request);
        assertRefreshCookieSet(response, "raw_refresh_token");
    }

    @Test
    void refreshRotatesTokenAndSetsNewCookie() {
        AuthResponse expected = new AuthResponse("new-token", "Bearer", 3600, "user-1", "user@example.com");
        AuthResult authResult = new AuthResult(expected, "rotated_refresh_token");
        when(authService.refresh("incoming_refresh_token")).thenReturn(authResult);
        when(refreshTokenService.getTtlDays()).thenReturn(30L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthResponse actual = authController.refresh("incoming_refresh_token", response);

        assertThat(actual).isEqualTo(expected);
        verify(authService).refresh("incoming_refresh_token");
        assertRefreshCookieSet(response, "rotated_refresh_token");
    }

    @Test
    void refreshWithoutCookieIsRejectedWithoutCallingAuthService() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
            BadCredentialsException.class,
            () -> authController.refresh(null, response)
        );

        verifyNoInteractions(authService);
    }

    @Test
    void logoutRevokesTokenAndClearsCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authController.logout("existing_refresh_token", response);

        verify(authService).logout("existing_refresh_token");
        assertRefreshCookieCleared(response);
    }

    @Test
    void logoutWithoutCookieStillClearsCookieButSkipsRevocation() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authController.logout(null, response);

        verify(authService, never()).logout(anyString());
        assertRefreshCookieCleared(response);
    }

    private void assertRefreshCookieSet(MockHttpServletResponse response, String expectedValue) {
        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader)
            .contains("refresh_token=" + expectedValue)
            .contains("HttpOnly")
            .contains("Secure")
            .contains("SameSite=Lax")
            .contains("Path=/api/auth");
    }

    private void assertRefreshCookieCleared(MockHttpServletResponse response) {
        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader)
            .contains("refresh_token=")
            .contains("Max-Age=0");
    }
}
