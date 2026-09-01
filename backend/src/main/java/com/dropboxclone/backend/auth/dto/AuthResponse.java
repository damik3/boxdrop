package com.dropboxclone.backend.auth.dto;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    String userId,
    String email
) {
}
