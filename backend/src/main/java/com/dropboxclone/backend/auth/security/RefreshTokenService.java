package com.dropboxclone.backend.auth.security;

import com.dropboxclone.backend.auth.model.RefreshToken;
import com.dropboxclone.backend.auth.repository.RefreshTokenRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final RefreshTokenProperties properties;

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public record Issued(String rawToken, RefreshToken record) {
    }

    public record Rotated(String userId, String rawToken) {
    }

    public Issued issue(String userId) {
        String raw = generateRawToken();
        RefreshToken saved = repository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(raw))
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(properties.ttlDays(), java.time.temporal.ChronoUnit.DAYS))
                .build());
        return new Issued(raw, saved);
    }

    public Rotated rotate(String rawToken) {
        RefreshToken existing = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (existing.getRevokedAt() != null) {
            // Token was already used once before -> possible theft, kill the whole family.
            revokeAllForUser(existing.getUserId());
            throw new BadCredentialsException("Refresh token reuse detected");
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token expired");
        }

        Issued next = issue(existing.getUserId());
        existing.setRevokedAt(Instant.now());
        existing.setReplacedByTokenId(next.record().getId());
        repository.save(existing);

        return new Rotated(existing.getUserId(), next.rawToken());
    }

    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            repository.save(token);
        });
    }

    public void revokeAllForUser(String userId) {
        repository.findByUserIdAndRevokedAtIsNull(userId).forEach(token -> {
            token.setRevokedAt(Instant.now());
            repository.save(token);
        });
    }

    public long getTtlDays() {
        return properties.ttlDays();
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}