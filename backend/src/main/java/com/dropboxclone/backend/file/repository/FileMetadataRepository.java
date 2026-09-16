package com.dropboxclone.backend.file.repository;

import com.dropboxclone.backend.file.model.FileMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {
    List<FileMetadata> findAllByUploadedByUserId(String uploadedByUserId);
    Optional<FileMetadata> findByStorageKey(String storageKey);
}
