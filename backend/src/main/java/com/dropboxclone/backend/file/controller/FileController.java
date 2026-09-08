package com.dropboxclone.backend.file.controller;


import com.dropboxclone.backend.auth.security.AuthenticatedUser;
import com.dropboxclone.backend.file.request.GetPresignedUrlRequest;
import com.dropboxclone.backend.file.request.MarkUploadCompletedRequest;
import com.dropboxclone.backend.file.response.DownloadFileResponse;
import com.dropboxclone.backend.file.response.GetFilesResponse;
import com.dropboxclone.backend.file.response.GetPresignedUrlResponse;
import com.dropboxclone.backend.file.service.FileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping
    public List<GetFilesResponse> getFiles(Authentication authentication) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        assert user != null;
        return fileService.
                getFiles(user.id())
                .stream()
                .map(fileMetadata ->
                        new GetFilesResponse(
                                fileMetadata.getId(),
                                fileMetadata.getName(),
                                fileMetadata.getSize(),
                                fileMetadata.getMimeType(),
                                fileMetadata.getUploadedByUserId(),
                                null
                        )
                )
                .toList();
    }

    @PostMapping("/upload/presigned-url-for-upload")
    public GetPresignedUrlResponse getPresignedUrl(Authentication authentication, @RequestBody GetPresignedUrlRequest request) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        assert user != null;
        return fileService.getPresignedUrlForUpload(user.id(), request);
    }

    @PostMapping("/upload/mark-upload-completed")
    public void markUploadCompleted(Authentication authentication, @RequestBody MarkUploadCompletedRequest request) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        assert user != null;
        fileService.markUploadCompleted(user.id(), request.fileId());
    }

    @GetMapping("/download/{fileId}")
    public DownloadFileResponse getDownloadLink(Authentication authentication,  @PathVariable String fileId) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        assert user != null;
        return new DownloadFileResponse(fileService.getDownloadLink(user.id(), fileId));
    }

}
