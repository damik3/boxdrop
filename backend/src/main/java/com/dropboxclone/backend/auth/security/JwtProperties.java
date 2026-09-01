package com.dropboxclone.backend.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.jwt")
public record JwtProperties(
    String secret,
    long accessTokenTtlSeconds,
    String issuer
) {
}
