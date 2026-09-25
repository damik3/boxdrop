package com.dropboxclone.backend.file.request;

public record GetPresignedUrlRequest(String name, Long size, String mimeType) {
}

