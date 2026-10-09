package com.dropboxclone.backend.file.repository;

import com.dropboxclone.backend.file.model.FileMetadata;
import com.dropboxclone.backend.file.model.FileUploadStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FileMetadataRepository extends MongoRepository<FileMetadata, String>, MultipartActivityRepository {
    List<FileMetadata> findAllByUploadedByUserId(String uploadedByUserId);

    Optional<FileMetadata> findByStorageKey(String storageKey);

    List<FileMetadata> findByStatusAndS3UploadIdIsNullAndCreatedAtBefore(FileUploadStatus status, Instant cutoff);

    @Query("{ 'status': ?0, 's3UploadId': { '$ne': null }, '$or': [ { 'updatedAt': { '$lt': ?1 } }, { 'updatedAt': null, 'createdAt': { '$lt': ?1 } } ] }")
    List<FileMetadata> findInactiveMultipartUploads(FileUploadStatus status, Instant cutoff);

    List<FileMetadata> findByStatusAndS3UploadIdIsNotNull(FileUploadStatus status);

    Optional<FileMetadata> findByUploadedByUserIdAndNameAndFingerprint(String uploadedByUserId,
            String name,
            String fingerprint);

    @Query("{ '_id': ?0, 'status': ?1, 's3UploadId': { '$ne': null }, '$or': [ { 'updatedAt': { '$lt': ?2 } }, { 'updatedAt': null, 'createdAt': { '$lt': ?2 } } ] }")
    @Update("{ '$set': { 'status': ?3, 'updatedAt': ?4 } }")
    long expireMultipart(String fileId, FileUploadStatus pending, Instant cutoff, FileUploadStatus failed, Instant now);

    @Query("{ '_id': ?0, 'status': ?1, 's3UploadId': null, 'createdAt': { '$lt': ?2 } }")
    @Update("{ '$set': { 'status': ?3, 'updatedAt': ?4 } }")
    long expireSingleUpload(String fileId, FileUploadStatus pending, Instant cutoff, FileUploadStatus next, Instant now);

    @Query("{ '_id': ?0, 'status': ?1 }")
    @Update("{ '$set': { 'status': ?2, 'updatedAt': ?3 } }")
    long transitionStatus(String fileId, FileUploadStatus current, FileUploadStatus next, Instant now);

    @Query("{ '_id': ?0, 'status': ?1, 's3UploadId': ?2 }")
    @Update("{ '$set': { 's3UploadId': null } }")
    long clearAbortedUpload(String fileId, FileUploadStatus failed, String uploadId);

}
