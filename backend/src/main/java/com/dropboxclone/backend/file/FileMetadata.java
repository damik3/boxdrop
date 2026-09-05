package com.dropboxclone.backend.file;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Builder(toBuilder = true)
@Document(collection = "file_metadata")
public class FileMetadata {
    @Id
    String id;

    String name;

    Integer size;

    String mimeType;

    String uploadedByUserId;

    String url;
}
