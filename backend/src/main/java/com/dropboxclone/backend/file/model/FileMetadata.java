package com.dropboxclone.backend.file.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder(toBuilder = true)
@Document(collection = "file_metadata")
public class FileMetadata {
    @Id
    String id;

    String name;

    Integer size;

    String mimeType;

    String uploadedByUserId;

    String storageKey;

    FileUploadStatus status;

    Instant createdAt;

    Instant updatedAt;
}
