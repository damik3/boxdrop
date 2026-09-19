package com.dropboxclone.backend.file.service;

import com.dropboxclone.backend.file.model.FileMetadata;
import com.dropboxclone.backend.file.model.FileUploadStatus;
import com.dropboxclone.backend.file.model.SharedFile;
import com.dropboxclone.backend.file.repository.FileMetadataRepository;
import com.dropboxclone.backend.file.repository.SharedFileRepository;
import com.dropboxclone.backend.file.request.GetPresignedUrlRequest;
import com.dropboxclone.backend.file.response.GetPresignedUrlResponse;
import com.dropboxclone.backend.user.model.User;
import com.dropboxclone.backend.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class FileService {
    private static final long MAX_SIZE = 50L * 1024 * 1024; // 50MB
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "application/pdf");
    static final Duration PRESIGN_TTL = Duration.ofMinutes(10);
    static final Duration STALE_PENDING_TTL = Duration.ofMinutes(15);

    private final FileMetadataRepository fileMetadataRepository;
    private final S3Service s3Service;
    private final SharedFileRepository sharedFileRepository;
    private final UserRepository userRepository;

    public FileService(FileMetadataRepository fileMetadataRepository, S3Service s3Service, SharedFileRepository sharedFileRepository, UserRepository userRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.s3Service = s3Service;
        this.sharedFileRepository = sharedFileRepository;
        this.userRepository = userRepository;
    }

    public List<FileMetadata> getFiles(String userId) {
        List<FileUploadStatus> statusesToBeReturned = List.of(
                FileUploadStatus.COMPLETED,
                FileUploadStatus.PENDING,
                FileUploadStatus.FAILED
        );
        return fileMetadataRepository.findAllByUploadedByUserId(userId)
                .stream()
                .filter(fileMetadata -> statusesToBeReturned.contains(fileMetadata.getStatus()))
                .toList();
    }

    public GetPresignedUrlResponse getPresignedUrlForUpload(String userId, GetPresignedUrlRequest getPresignedUrlRequest) {
        validate(getPresignedUrlRequest);

        Instant now = Instant.now();
        FileMetadata fileMetadata = fileMetadataRepository.save(
                FileMetadata.builder()
                        .name(getPresignedUrlRequest.name())
                        .size(getPresignedUrlRequest.size())
                        .mimeType(getPresignedUrlRequest.mimeType())
                        .uploadedByUserId(userId)
                        .status(FileUploadStatus.PENDING)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()
        );

        String key = "users/%s/%s-%s".formatted(userId, fileMetadata.getId(), getPresignedUrlRequest.name());
        fileMetadata.setStorageKey(key);
        fileMetadataRepository.save(fileMetadata);

        return new GetPresignedUrlResponse(
                fileMetadata.getId(),
                s3Service.presignPutUrl(key, fileMetadata.getMimeType(), PRESIGN_TTL)
        );
    }

    private void validate(GetPresignedUrlRequest req) {
        if (req.size() <= 0 || req.size() > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file size");
        }
        if (!ALLOWED_TYPES.contains(req.mimeType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported content type");
        }
    }

    public String getDownloadLink(String userId, String fileId) {
        FileMetadata file = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        boolean fileUploadedByUser = file.getUploadedByUserId().equals(userId);
        boolean fileSharedWithUser = sharedFileRepository.findByUserIdAndFileId(userId, fileId).isPresent();

        if (!fileUploadedByUser && !fileSharedWithUser) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }

        if (file.getStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing storage key");
        }


        if (file.getStatus() != FileUploadStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File not uploaded");
        }

        return s3Service.presignGetUrl(file.getStorageKey(), PRESIGN_TTL);
    }

    public void deleteFile(String userId, String fileId) {
        FileMetadata file = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        if (!file.getUploadedByUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }

        if (file.getStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing storage key");
        }

        s3Service.deleteObject(file.getStorageKey());
        sharedFileRepository.deleteByFileId(fileId);
        fileMetadataRepository.delete(file);
    }

    public void completeByStorageKey(String storageKey) throws NoSuchElementException {
        FileMetadata file = fileMetadataRepository.findByStorageKey(storageKey)
                .orElse(null);
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
        List<FileMetadata> stale = fileMetadataRepository.findByStatusAndCreatedAtBefore(
                FileUploadStatus.PENDING,
                cutoff
        );
        for (FileMetadata file : stale) {
            boolean objectExists = file.getStorageKey() != null && s3Service.objectExists(file.getStorageKey());
            file.setStatus(objectExists ? FileUploadStatus.COMPLETED : FileUploadStatus.FAILED);
            file.setUpdatedAt(now);
            fileMetadataRepository.save(file);
        }
    }

    public List<FileMetadata> getSharedFiles(String userId) {
        List<String> sharedFileIds = sharedFileRepository
                .findByUserId(userId)
                .stream()
                .map(SharedFile::getFileId)
                .toList();
        return fileMetadataRepository.findAllById(sharedFileIds);
    }

    public List<User> getFileShares(String userId, String fileId) {
        List<String> userIds = sharedFileRepository
                .findByFileIdAndSharedByUserId(fileId, userId)
                .stream()
                .map(SharedFile::getUserId)
                .toList();
        return userRepository.findAllById(userIds);
    }

    public void shareFile(String sharedByUserId, String fileId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + email));
        FileMetadata fileMetadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("File not found: " + fileId));
        if (!fileMetadata.getUploadedByUserId().equals(sharedByUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
        if (fileMetadata.getStatus() != FileUploadStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File not uploaded");
        }
        sharedFileRepository.save(
                SharedFile
                        .builder()
                        .sharedByUserId(sharedByUserId)
                        .fileId(fileId)
                        .userId(user.getId())
                        .createdAt(Instant.now())
                        .build()
        );
    }

    public void unshareFile(String sharedByUserId, String fileId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + email));
        FileMetadata fileMetadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("File not found: " + fileId));
        if (!fileMetadata.getUploadedByUserId().equals(sharedByUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
        sharedFileRepository.deleteByUserIdAndFileId(user.getId(), fileId);
    }

}
