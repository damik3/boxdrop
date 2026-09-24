package com.dropboxclone.backend.file.request;

public record InitiateMultipartUploadRequest(
        String filename,
        String fingerprint,
        Integer size,
        String mimeType,
        Integer numChunks
) {
}
