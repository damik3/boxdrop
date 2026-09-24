package com.dropboxclone.backend.file.request;

public record FileExistsRequest(
        String filename,
        String fingerprint
) {}
