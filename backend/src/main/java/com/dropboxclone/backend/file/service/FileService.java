package com.dropboxclone.backend.file.service;

import com.dropboxclone.backend.file.model.FileMetadata;
import com.dropboxclone.backend.file.model.FileUploadStatus;
import com.dropboxclone.backend.file.repository.FileMetadataRepository;
import com.dropboxclone.backend.file.request.GetPresignedUrlRequest;
import com.dropboxclone.backend.file.response.GetPresignedUrlResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class FileService {
    private static final long MAX_SIZE = 50L * 1024 * 1024; // 50MB
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "application/pdf");

    private final FileMetadataRepository fileMetadataRepository;
    private final S3Service s3Service;

    public FileService(FileMetadataRepository fileMetadataRepository, S3Service s3Service) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.s3Service = s3Service;
    }

    public List<FileMetadata> getFiles(String userId) {
        List<FileUploadStatus> statusesToBeReturned = List.of(FileUploadStatus.COMPLETED, FileUploadStatus.PENDING);
        return fileMetadataRepository.findAllByUploadedByUserId(userId)
                .stream()
                .filter(fileMetadata -> statusesToBeReturned.contains(fileMetadata.getStatus()))
                .toList();
    }

    public GetPresignedUrlResponse getPresignedUrlForUpload(String userId, GetPresignedUrlRequest getPresignedUrlRequest) {
        validate(getPresignedUrlRequest);

        FileMetadata fileMetadata = fileMetadataRepository.save(
                FileMetadata.builder()
                        .name(getPresignedUrlRequest.name())
                        .size(getPresignedUrlRequest.size())
                        .mimeType(getPresignedUrlRequest.mimeType())
                        .uploadedByUserId(userId)
                        .status(FileUploadStatus.PENDING)
                        .build()
        );

        String key = "users/%s/%s-%s".formatted(userId, fileMetadata.getId(), getPresignedUrlRequest.name());
        fileMetadata.setStorageKey(key);
        fileMetadataRepository.save(fileMetadata);

        PresignedPutObjectRequest presignedPutObjectRequest =
                s3Service.presignPut(key, fileMetadata.getMimeType(), Duration.ofMinutes(10));

        return new GetPresignedUrlResponse(fileMetadata.getId(), presignedPutObjectRequest.url().toString());
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

        if (!file.getUploadedByUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }

        if (file.getStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing storage key");
        }


        if (file.getStatus() != FileUploadStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File not uploaded");
        }

        return s3Service.presignGet(file.getStorageKey(), Duration.ofMinutes(10)).url().toString();
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
        fileMetadataRepository.save(file);
    }

}
