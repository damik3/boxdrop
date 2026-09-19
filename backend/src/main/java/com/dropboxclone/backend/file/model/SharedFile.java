package com.dropboxclone.backend.file.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder(toBuilder = true)
@Document(collection = "shared_file")
@CompoundIndex(name = "idx_user_file", def = "{'userId': 1, 'fileId': 1}", unique = true)
public class SharedFile {

    String userId;

    String fileId;

    @Indexed
    String sharedByUserId;

    Instant createdAt;
}
