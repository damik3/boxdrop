package com.dropboxclone.backend.file.repository;

import com.dropboxclone.backend.file.model.FileChunkStatus;
import com.dropboxclone.backend.file.model.FileUploadStatus;

import java.time.Instant;

public interface MultipartActivityRepository {
    boolean touchMultipart(String fileId, FileUploadStatus status, Instant now);

    boolean markChunkUploaded(String fileId, FileUploadStatus pending, Integer partNumber, FileChunkStatus status,
            String fingerprint, String etag, Instant now);
}
