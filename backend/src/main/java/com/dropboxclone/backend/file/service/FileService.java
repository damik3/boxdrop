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
import java.util.Optional;
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
        return fileMetadataRepository.findAllByUploadedByUserId(userId)
                .stream()
                .filter(fileMetadata -> fileMetadata.getStatus() == FileUploadStatus.COMPLETED)
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

    public void markUploadCompleted(String userId, String fileId) {
        Optional<FileMetadata> fileMetadataOptional = fileMetadataRepository.findById(fileId);

        if (fileMetadataOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }

        fileMetadataOptional.ifPresent(fileMetadata -> {
            if (!fileMetadata.getUploadedByUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not owner of file");
            }
            if (fileMetadata.getStatus() == FileUploadStatus.COMPLETED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "File is already uploaded");
            }
            String key = fileMetadata.getStorageKey();
            if (key == null || !s3Service.objectExists(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded object not found in storage");
            }
            fileMetadata.setStatus(FileUploadStatus.COMPLETED);
            fileMetadataRepository.save(fileMetadata);
        });
    }
}
