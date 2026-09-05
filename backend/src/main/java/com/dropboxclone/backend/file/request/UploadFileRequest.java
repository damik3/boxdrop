package com.dropboxclone.backend.file.request;

public record UploadFileRequest(String name, Integer size, String mimeType) {
}

