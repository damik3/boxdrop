package com.dropboxclone.backend.auth.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder(toBuilder = true)
@Document(collection = "refresh_tokens")
public class RefreshToken {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed(unique = true)
    private String tokenHash;

    private Instant createdAt;
    private Instant expiresAt;
    private Instant revokedAt;
    private String replacedByTokenId;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
