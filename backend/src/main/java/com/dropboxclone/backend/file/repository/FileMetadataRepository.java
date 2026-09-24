package com.dropboxclone.backend.file.repository;

import com.dropboxclone.backend.file.model.FileChunkStatus;
import com.dropboxclone.backend.file.model.FileMetadata;
import com.dropboxclone.backend.file.model.FileUploadStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {
    List<FileMetadata> findAllByUploadedByUserId(String uploadedByUserId);

    Optional<FileMetadata> findByStorageKey(String storageKey);

    List<FileMetadata> findByStatusAndCreatedAtBefore(FileUploadStatus status, Instant cutoff);

    Optional<FileMetadata> findByUploadedByUserIdAndNameAndFingerprint(String uploadedByUserId,
            String name,
            String fingerprint);

    @Query("{ '_id': ?0, 'fileChunks.partNumber': ?1 }")
    @Update("{ '$set': { 'fileChunks.$.fileChunkStatus': ?2, 'fileChunks.$.fingerprint': ?3, 'fileChunks.$.etag': ?4 } }")
    void markChunkUploaded(String fileId, Integer partNumber, FileChunkStatus status, String fingerprint, String etag);
}
