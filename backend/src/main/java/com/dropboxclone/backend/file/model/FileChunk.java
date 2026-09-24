package com.dropboxclone.backend.file.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder(toBuilder = true)
public class FileChunk {
    String id;
    FileChunkStatus fileChunkStatus;
    Integer partNumber;
    String fingerprint;
    String etag;
}
