package com.dropboxclone.backend.file.request;

public record InitiateMultipartUploadRequest(String filename,
                                             String fingerprint,
                                             Long size,
                                             String mimeType,
                                             Integer numChunks) {
}
