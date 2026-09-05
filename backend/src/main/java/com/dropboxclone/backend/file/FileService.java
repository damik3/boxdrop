package com.dropboxclone.backend.file;

import com.dropboxclone.backend.file.request.UploadFileRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FileService {

    private final FileMetadataRepository fileMetadataRepository;

    public FileService(FileMetadataRepository fileMetadataRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
    }

    public List<FileMetadata> getFiles(String userId) {
        return fileMetadataRepository.findAllByUploadedByUserId(userId);
    }

    public String uploadFile(String userId, UploadFileRequest uploadFileRequest) {
        fileMetadataRepository.save(
                FileMetadata.builder()
                        .name(uploadFileRequest.name())
                        .size(uploadFileRequest.size())
                        .mimeType(uploadFileRequest.mimeType())
                        .uploadedByUserId(userId)
                        .build()
        );
        return "Ok";
    }
}
