package com.dropboxclone.backend.auth.service;

import com.dropboxclone.backend.auth.dto.AuthResponse;
import com.dropboxclone.backend.auth.dto.AuthResult;
import com.dropboxclone.backend.auth.dto.LoginRequest;
import com.dropboxclone.backend.auth.dto.RegisterRequest;
import com.dropboxclone.backend.auth.security.JwtService;
import com.dropboxclone.backend.auth.security.RefreshTokenService;
import com.dropboxclone.backend.user.model.User;
import com.dropboxclone.backend.user.repository.UserRepository;
import java.time.Instant;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResult register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(CONFLICT, "Email is already registered");
        }

        User user = userRepository.save(User.builder()
            .email(normalizedEmail)
            .passwordHash(passwordEncoder.encode(request.password()))
            .createdAt(Instant.now())
            .build());

        return toAuthResult(user);
    }

    public AuthResult login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return toAuthResult(user);
    }

    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(rawRefreshToken);
        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));

        AuthResponse authResponse = new AuthResponse(
                jwtService.generateToken(user.getId(), user.getEmail()),
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                user.getId(),
                user.getEmail()
        );
        return new AuthResult(authResponse, rotated.rawToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private AuthResult toAuthResult(User user) {
        AuthResponse authResponse = new AuthResponse(
                jwtService.generateToken(user.getId(), user.getEmail()),
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                user.getId(),
                user.getEmail()
        );
        RefreshTokenService.Issued issued = refreshTokenService.issue(user.getId());
        return new AuthResult(authResponse, issued.rawToken());
    }

}
