package com.dropboxclone.backend.file.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PendingUploadSweeper {
    private final FileService fileService;

    public PendingUploadSweeper(FileService fileService) {
        this.fileService = fileService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void sweep() {
        fileService.expireStalePendingUploads(Instant.now());
    }
}
