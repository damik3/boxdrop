package com.dropboxclone.backend.file.controller;


import com.dropboxclone.backend.auth.security.AuthenticatedUser;
import com.dropboxclone.backend.file.request.GetPresignedUrlRequest;
import com.dropboxclone.backend.file.request.ShareFileRequest;
import com.dropboxclone.backend.file.request.UnshareFileRequest;
import com.dropboxclone.backend.file.response.DownloadFileResponse;
import com.dropboxclone.backend.file.response.GetFileSharesResponse;
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
        return fileService
                .getFiles(user.id())
                .stream()
                .map(fileMetadata ->
                        new GetFilesResponse(
                                fileMetadata.getId(),
                                fileMetadata.getName(),
                                fileMetadata.getSize(),
                                fileMetadata.getMimeType(),
                                fileMetadata.getUploadedByUserId(),
                                fileMetadata.getStatus().toString()
                        )
                )
                .toList();
    }

    @PostMapping("/upload/presigned-url-for-upload")
    public GetPresignedUrlResponse getPresignedUrl(Authentication authentication, @RequestBody GetPresignedUrlRequest request) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        return fileService.getPresignedUrlForUpload(user.id(), request);
    }

    @GetMapping("/download/{fileId}")
    public DownloadFileResponse getDownloadLink(Authentication authentication, @PathVariable String fileId) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        return new DownloadFileResponse(fileService.getDownloadLink(user.id(), fileId));
    }

    @DeleteMapping("/{fileId}")
    public void deleteFile(Authentication authentication, @PathVariable String fileId) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        fileService.deleteFile(user.id(), fileId);
    }

    @GetMapping("/shared")
    public List<GetFilesResponse> getSharedFiles(Authentication authentication) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        return fileService
                .getSharedFiles(user.id())
                .stream()
                .map(fileMetadata ->
                        new GetFilesResponse(
                                fileMetadata.getId(),
                                fileMetadata.getName(),
                                fileMetadata.getSize(),
                                fileMetadata.getMimeType(),
                                fileMetadata.getUploadedByUserId(),
                                fileMetadata.getStatus().toString()
                        )
                )
                .toList();
    }

    @GetMapping("/{fileId}/shares")
    public List<GetFileSharesResponse> getFileShares(Authentication authentication, @PathVariable String fileId) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        return fileService
                .getFileShares(user.id(), fileId)
                .stream()
                .map(u ->  new GetFileSharesResponse(u.getId(), u.getEmail()))
                .toList();
    }

    @PostMapping("/{fileId}/share")
    public void shareFile(Authentication authentication, @PathVariable String fileId, @RequestBody ShareFileRequest request) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        fileService.shareFile(user.id(), fileId, request.email());
    }

    @DeleteMapping("/{fileId}/share")
    public void unshareFile(Authentication authentication, @PathVariable String fileId, @RequestBody UnshareFileRequest request) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        fileService.unshareFile(user.id(), fileId, request.email());
    }

}
