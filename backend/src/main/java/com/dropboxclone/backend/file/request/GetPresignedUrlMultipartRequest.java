package com.dropboxclone.backend.file.request;

public record GetPresignedUrlMultipartRequest(String fileId, String uploadId, int partNumber) {
}
