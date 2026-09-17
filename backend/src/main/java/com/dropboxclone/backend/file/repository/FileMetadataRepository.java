package com.dropboxclone.backend.file.repository;

import com.dropboxclone.backend.file.model.FileMetadata;
import com.dropboxclone.backend.file.model.FileUploadStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {
    List<FileMetadata> findAllByUploadedByUserId(String uploadedByUserId);
    Optional<FileMetadata> findByStorageKey(String storageKey);
    List<FileMetadata> findByStatusAndCreatedAtBefore(FileUploadStatus status, Instant cutoff);
}
