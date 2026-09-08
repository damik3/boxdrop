package com.dropboxclone.backend.file.response;

public record GetPresignedUrlResponse(String fileId, String presignedUrl) {
}
