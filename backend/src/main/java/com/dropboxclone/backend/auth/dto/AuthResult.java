package com.dropboxclone.backend.auth.dto;

public record AuthResult(AuthResponse authResponse, String rawRefreshToken) {
}
