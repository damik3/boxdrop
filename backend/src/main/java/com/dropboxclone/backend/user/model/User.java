package com.dropboxclone.backend.user.model;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Builder(toBuilder = true)
@Document(collection = "users")
public class User {

    @Id
    private final String id;

    @Indexed(unique = true)
    private final String email;

    private final String passwordHash;

    private final Instant createdAt;
}
