package com.dropboxclone.backend.auth.controller;

import com.dropboxclone.backend.auth.dto.AuthResponse;
import com.dropboxclone.backend.auth.dto.AuthResult;
import com.dropboxclone.backend.auth.dto.LoginRequest;
import com.dropboxclone.backend.auth.dto.RegisterRequest;
import com.dropboxclone.backend.auth.security.RefreshTokenService;
import com.dropboxclone.backend.auth.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthResult result = authService.register(request);
        setRefreshCookie(response, result.rawRefreshToken());
        return result.authResponse();
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResult result = authService.login(request);
        setRefreshCookie(response, result.rawRefreshToken());
        return result.authResponse();
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String rawRefreshToken, HttpServletResponse response) {
        if (rawRefreshToken == null) {
            throw new BadCredentialsException("Missing refresh token");
        }
        AuthResult result = authService.refresh(rawRefreshToken);
        setRefreshCookie(response, result.rawRefreshToken());
        return result.authResponse();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String rawRefreshToken, HttpServletResponse response) {
        if (rawRefreshToken != null) {
            authService.logout(rawRefreshToken);
        }
        clearRefreshCookie(response);
    }

    private void setRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken).httpOnly(true).secure(true).sameSite("Lax").path(REFRESH_COOKIE_PATH).maxAge(Duration.ofDays(refreshTokenService.getTtlDays())).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "").httpOnly(true).secure(true).sameSite("Lax").path(REFRESH_COOKIE_PATH).maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
