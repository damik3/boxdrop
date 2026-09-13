package com.dropboxclone.backend.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.refresh")
public record RefreshTokenProperties (
    long ttlDays
) {}
