package com.dropboxclone.backend.file.request;

public record PatchMultipartUploadRequest(String fileId, Integer partNumber, String fingerprint, String etag) {
}
