package com.dropboxclone.backend.file.request;

public record PatchMultipartUploadRequest(String fileId,
                                          String uploadId,
                                          Integer partNumber,
                                          String fingerprint,
                                          String etag) {
}
