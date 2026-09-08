package com.dropboxclone.backend.file.request;

public record GetPresignedUrlRequest(String name, Integer size, String mimeType) {
}

