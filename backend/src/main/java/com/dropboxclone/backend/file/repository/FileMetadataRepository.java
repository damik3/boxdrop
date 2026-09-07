package com.dropboxclone.backend.file.repository;

import com.dropboxclone.backend.file.model.FileMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {
    List<FileMetadata> findAllByUploadedByUserId(String uploadedByUserId);
}
