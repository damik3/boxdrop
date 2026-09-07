package com.dropboxclone.backend.file.service;

import com.dropboxclone.backend.file.model.FileMetadata;
import com.dropboxclone.backend.file.model.FileUploadStatus;
import com.dropboxclone.backend.file.repository.FileMetadataRepository;
import com.dropboxclone.backend.file.request.UploadFileRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.util.List;
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
        return fileMetadataRepository.findAllByUploadedByUserId(userId);
    }

    public String uploadFile(String userId, UploadFileRequest uploadFileRequest) {
        validate(uploadFileRequest);

        FileMetadata fileMetadata = fileMetadataRepository.save(
                FileMetadata.builder()
                        .name(uploadFileRequest.name())
                        .size(uploadFileRequest.size())
                        .mimeType(uploadFileRequest.mimeType())
                        .uploadedByUserId(userId)
                        .status(FileUploadStatus.PENDING)
                        .build()
        );

        String key = "users/%s/%s-%s".formatted(userId, fileMetadata.getId(), uploadFileRequest.name());

        PresignedPutObjectRequest presignedPutObjectRequest =
                s3Service.presignPut(key, fileMetadata.getMimeType(), Duration.ofMinutes(10));


        String url = presignedPutObjectRequest.url().toString();
        System.out.println("Presigned URL for " + userId + ": " + url);
        return url;
    }

    private void validate(UploadFileRequest req) {
        if (req.size() <= 0 || req.size() > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file size");
        }
        if (!ALLOWED_TYPES.contains(req.mimeType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported content type");
        }
    }
}
