package com.dropboxclone.backend.file.service;

import com.dropboxclone.backend.common.ApiError;
import com.dropboxclone.backend.file.model.*;
import com.dropboxclone.backend.file.repository.FileMetadataRepository;
import com.dropboxclone.backend.file.repository.SharedFileRepository;
import com.dropboxclone.backend.file.request.GetPresignedUrlRequest;
import com.dropboxclone.backend.file.response.GetPresignedUrlResponse;
import com.dropboxclone.backend.file.response.InitiateMultipartUploadResponse;
import com.dropboxclone.backend.user.model.User;
import com.dropboxclone.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.Part;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class FileService {
    private static final long MAX_SIZE = 50L * 1024 * 1024 * 1024; // 50GB
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "application/pdf");
    static final Duration PRESIGN_TTL = Duration.ofMinutes(10);
    static final Duration STALE_PENDING_TTL = Duration.ofMinutes(15);

    private final FileMetadataRepository fileMetadataRepository;
    private final S3Service s3Service;
    private final SharedFileRepository sharedFileRepository;
    private final UserRepository userRepository;

    public FileService(FileMetadataRepository fileMetadataRepository,
            S3Service s3Service,
            SharedFileRepository sharedFileRepository,
            UserRepository userRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.s3Service = s3Service;
        this.sharedFileRepository = sharedFileRepository;
        this.userRepository = userRepository;
    }

    public List<FileMetadata> getFiles(String userId) {
        List<FileUploadStatus> statusesToBeReturned = List.of(FileUploadStatus.COMPLETED,
                FileUploadStatus.PENDING,
                FileUploadStatus.FAILED);
        return fileMetadataRepository.findAllByUploadedByUserId(userId)
                .stream()
                .filter(fileMetadata -> statusesToBeReturned.contains(fileMetadata.getStatus()))
                .toList();
    }

    public GetPresignedUrlResponse getPresignedUrlForUpload(String userId,
            GetPresignedUrlRequest getPresignedUrlRequest) {
        validateSize(getPresignedUrlRequest.size());
        validateSupportedType(getPresignedUrlRequest.mimeType());

        Instant now = Instant.now();
        FileMetadata fileMetadata = fileMetadataRepository.save(FileMetadata.builder()
                .name(getPresignedUrlRequest.name())
                .size(getPresignedUrlRequest.size())
                .mimeType(getPresignedUrlRequest.mimeType())
                .uploadedByUserId(userId)
                .status(FileUploadStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build());

        String key = getKey(userId, fileMetadata.getId(), getPresignedUrlRequest.name());
        fileMetadata.setStorageKey(key);
        fileMetadataRepository.save(fileMetadata);

        return new GetPresignedUrlResponse(fileMetadata.getId(),
                s3Service.presignPutUrl(key, fileMetadata.getMimeType(), PRESIGN_TTL));
    }

    private void validateSize(Long size) {
        if (size <= 0 || size > MAX_SIZE) {
            throw ApiError.INVALID_FILE_SIZE.exception();
        }
    }

    private void validateSupportedType(String type) {
        if (!ALLOWED_TYPES.contains(type)) {
            throw ApiError.UNSUPPORTED_CONTENT_TYPE.exception();
        }
    }

    public String getDownloadLink(String userId, String fileId) {
        FileMetadata file = fileMetadataRepository.findById(fileId).orElseThrow(ApiError.FILE_NOT_FOUND::exception);

        boolean fileUploadedByUser = file.getUploadedByUserId().equals(userId);
        boolean fileSharedWithUser = sharedFileRepository.findByUserIdAndFileId(userId, fileId).isPresent();

        if (!fileUploadedByUser && !fileSharedWithUser) {
            throw ApiError.NOT_ALLOWED.exception();
        }

        if (file.getStorageKey() == null) {
            throw ApiError.MISSING_STORAGE_KEY.exception();
        }


        if (file.getStatus() != FileUploadStatus.COMPLETED) {
            throw ApiError.FILE_NOT_UPLOADED.exception();
        }

        return s3Service.presignGetUrl(file.getStorageKey(), PRESIGN_TTL);
    }

    public void deleteFile(String userId, String fileId) {
        FileMetadata file = fileMetadataRepository.findById(fileId).orElseThrow(ApiError.FILE_NOT_FOUND::exception);

        if (!file.getUploadedByUserId().equals(userId)) {
            throw ApiError.NOT_ALLOWED.exception();
        }

        if (file.getStorageKey() == null) {
            throw ApiError.MISSING_STORAGE_KEY.exception();
        }

        s3Service.deleteObject(file.getStorageKey());
        if (file.getS3UploadId() != null && file.getStatus() != FileUploadStatus.COMPLETED) {
            s3Service.abortMultipartUpload(file.getStorageKey(), file.getS3UploadId());
        }
        sharedFileRepository.deleteByFileId(fileId);
        fileMetadataRepository.delete(file);
    }

    public void completeByStorageKey(String storageKey) throws NoSuchElementException {
        FileMetadata file = fileMetadataRepository.findByStorageKey(storageKey).orElse(null);
        if (file == null) {
            return; // unknown PUT — do not create metadata
        }
        if (file.getStatus() == FileUploadStatus.COMPLETED) {
            return; // idempotent
        }
        if (!s3Service.objectExists(storageKey)) {
            throw new NoSuchElementException("File %s not found".formatted(storageKey));
        }
        file.setStatus(FileUploadStatus.COMPLETED);
        file.setUpdatedAt(Instant.now());
        fileMetadataRepository.save(file);
    }

    public void expireStalePendingUploads(Instant now) {
        Instant cutoff = now.minus(STALE_PENDING_TTL);
        List<FileMetadata> stale = fileMetadataRepository.findByStatusAndCreatedAtBefore(FileUploadStatus.PENDING,
                cutoff);
        for (FileMetadata file : stale) {
            boolean objectExists = file.getStorageKey() != null && s3Service.objectExists(file.getStorageKey());
            file.setStatus(objectExists ? FileUploadStatus.COMPLETED : FileUploadStatus.FAILED);
            file.setUpdatedAt(now);
            fileMetadataRepository.save(file);
        }
    }

    public List<FileMetadata> getSharedFiles(String userId) {
        List<String> sharedFileIds = sharedFileRepository.findByUserId(userId)
                .stream()
                .map(SharedFile::getFileId)
                .toList();
        return fileMetadataRepository.findAllById(sharedFileIds)
                .stream()
                .filter(file -> file.getStatus() == FileUploadStatus.COMPLETED)
                .toList();
    }

    public Map<String, String> getUserEmails(Collection<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, User::getEmail));
    }

    public List<User> getFileShares(String userId, String fileId) {
        requireOwnedFile(userId, fileId);
        List<String> userIds = sharedFileRepository.findByFileIdAndSharedByUserId(fileId, userId)
                .stream()
                .map(SharedFile::getUserId)
                .toList();
        return userRepository.findAllById(userIds);
    }

    public void shareFile(String sharedByUserId, String fileId, String email) {
        User recipient = findUserByEmail(email);
        FileMetadata file = requireOwnedFile(sharedByUserId, fileId);
        if (recipient.getId().equals(sharedByUserId)) {
            throw ApiError.CANNOT_SHARE_WITH_SELF.exception();
        }
        if (file.getStatus() != FileUploadStatus.COMPLETED) {
            throw ApiError.FILE_NOT_UPLOADED.exception();
        }
        if (sharedFileRepository.findByUserIdAndFileId(recipient.getId(), fileId).isPresent()) {
            return;
        }
        sharedFileRepository.save(SharedFile.builder()
                .sharedByUserId(sharedByUserId)
                .fileId(fileId)
                .userId(recipient.getId())
                .createdAt(Instant.now())
                .build());
    }

    public void unshareFile(String sharedByUserId, String fileId, String email) {
        User recipient = findUserByEmail(email);
        requireOwnedFile(sharedByUserId, fileId);
        sharedFileRepository.deleteByUserIdAndFileId(recipient.getId(), fileId);
    }

    public Optional<FileMetadata> exists(String userId, String filename, String fingerprint) {
        Optional<FileMetadata> fileMetadataOptional = fileMetadataRepository.findByUploadedByUserIdAndNameAndFingerprint(
                userId,
                filename,
                fingerprint);
        if (fileMetadataOptional.stream()
                .anyMatch(fileMetadata -> !fileMetadata.getUploadedByUserId().equals(userId))) {
            throw ApiError.NOT_ALLOWED.exception();
        }
        return fileMetadataOptional;
    }

    private String getKey(String userId, String fileId, String filename) {
        return "users/%s/%s-%s".formatted(userId, fileId, filename);
    }

    private User findUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw ApiError.EMAIL_REQUIRED.exception();
        }
        String normalizedEmail = email.trim().toLowerCase();
        return userRepository.findByEmail(normalizedEmail).orElseThrow(ApiError.USER_NOT_FOUND::exception);
    }

    private FileMetadata requireOwnedFile(String userId, String fileId) {
        FileMetadata file = fileMetadataRepository.findById(fileId).orElseThrow(ApiError.FILE_NOT_FOUND::exception);
        if (!file.getUploadedByUserId().equals(userId)) {
            throw ApiError.NOT_ALLOWED.exception();
        }
        return file;
    }

    public InitiateMultipartUploadResponse initiateMultipartUpload(String userId,
            String filename,
            String mimeType,
            Long size,
            String fingerprint,
            Integer numChunks) {
        validateSize(size);
        validateSupportedType(mimeType);

        Instant now = Instant.now();
        FileMetadata fileMetadata = fileMetadataRepository.save(FileMetadata.builder()
                .name(filename)
                .size(size)
                .mimeType(mimeType)
                .uploadedByUserId(userId)
                .status(FileUploadStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .fingerprint(fingerprint)
                .fileChunks(IntStream.rangeClosed(1, numChunks)
                        .mapToObj(i -> FileChunk.builder()
                                .partNumber(i)
                                .fileChunkStatus(FileChunkStatus.NOT_UPLOADED)
                                .build())
                        .toList())
                .build());

        String key = getKey(userId, fileMetadata.getId(), filename);
        CreateMultipartUploadResponse createMultipartUploadResponse = s3Service.multipartUpload(key, mimeType);
        String uploadId = createMultipartUploadResponse.uploadId();
        fileMetadata.setS3UploadId(uploadId);
        fileMetadata.setStorageKey(key);
        fileMetadataRepository.save(fileMetadata);
        return new InitiateMultipartUploadResponse(fileMetadata.getId());
    }

    public String presignUploadPart(String userId, String fileId, int partNumber) {
        FileMetadata fileMetadata = requireOwnedFile(userId, fileId);
        String key = fileMetadata.getStorageKey();
        Duration ttl = Duration.ofMinutes(15);
        return s3Service.presignUploadPart(key, fileMetadata.getS3UploadId(), partNumber, ttl);
    }

    public void patchMultipartUpload(String userId,
            String fileId,
            Integer partNumber,
            String fingerprint,
            String etag) {
        FileMetadata file = requireOwnedFile(userId, fileId);
        String key = file.getStorageKey();
        ListPartsResponse listPartsResponse = s3Service.listParts(key, file.getS3UploadId());
        boolean valid = listPartsResponse.parts()
                .stream()
                .anyMatch(part -> part.eTag().equals(etag) && Objects.equals(part.partNumber(), partNumber));
        if (valid) {
            fileMetadataRepository.markChunkUploaded(fileId, partNumber, FileChunkStatus.UPLOADED, fingerprint, etag);
        } else {
            throw ApiError.FILE_NOT_UPLOADED.exception();
        }

    }

    public void completeMultipartUpload(String userId, String fileId) {
        FileMetadata file = requireOwnedFile(userId, fileId);
        if (file.getFileChunks().isEmpty()) {
            throw ApiError.FILE_NOT_FOUND.exception();
        }
        if (file.getStatus().equals(FileUploadStatus.COMPLETED)) {
            return;
        }
        String key = file.getStorageKey();
        List<Part> parts = s3Service.listParts(key, file.getS3UploadId()).parts();

        Map<Integer, Part> partsByPartNumber = parts.stream()
                .collect(Collectors.toMap(Part::partNumber, Function.identity()));

        boolean complete = file.getFileChunks()
                .stream()
                .allMatch(chunk -> chunk.getFileChunkStatus() == FileChunkStatus.UPLOADED &&
                        partsByPartNumber.containsKey(chunk.getPartNumber()) &&
                        partsByPartNumber.get(chunk.getPartNumber()).eTag().equals(chunk.getEtag()));

        if (complete) {
            s3Service.completeMultipartUpload(key, file.getS3UploadId(), parts);
            file.setStatus(FileUploadStatus.COMPLETED);
            fileMetadataRepository.save(file);
        } else {
            throw ApiError.FILE_NOT_UPLOADED.exception();
        }
    }

    public List<FileChunk> getParts(String userId, String fileId) {
        FileMetadata fileMetadata = requireOwnedFile(userId, fileId);
        return fileMetadata.getFileChunks();
    }

}
