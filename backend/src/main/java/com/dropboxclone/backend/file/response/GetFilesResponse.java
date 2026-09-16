package com.dropboxclone.backend.file.response;

public record GetFilesResponse(
        String id,
        String name,
        Integer size,
        String mimeType,
        String uploadedBy,
        String url,
        String status
) {}
