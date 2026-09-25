package com.dropboxclone.backend.file.response;

public record GetFilesResponse(String id, String name, Long size, String mimeType, String uploadedBy, String status) {
}
