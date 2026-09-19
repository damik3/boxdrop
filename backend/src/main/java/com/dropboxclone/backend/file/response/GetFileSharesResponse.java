package com.dropboxclone.backend.file.response;

public record GetFileSharesResponse(
        String userId,
        String email
) {}
