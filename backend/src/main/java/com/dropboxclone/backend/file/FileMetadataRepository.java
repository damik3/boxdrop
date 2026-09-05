package com.dropboxclone.backend.file;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {
    List<FileMetadata> findAllByUploadedByUserId(String uploadedByUserId);
}
