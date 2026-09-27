package com.dropboxclone.backend.file.controller;


import com.dropboxclone.backend.auth.security.AuthenticatedUser;
import com.dropboxclone.backend.file.model.FileMetadata;
import com.dropboxclone.backend.file.request.*;
import com.dropboxclone.backend.file.response.*;
import com.dropboxclone.backend.file.service.FileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        return fileService.getFiles(user.id())
                .stream()
                .map(fileMetadata -> new GetFilesResponse(fileMetadata.getId(),
                        fileMetadata.getName(),
                        fileMetadata.getSize(),
                        fileMetadata.getMimeType(),
                        fileMetadata.getUploadedByUserId(),
                        fileMetadata.getStatus().toString()))
                .toList();
    }

    @PostMapping("/upload/presigned-url")
    public GetPresignedUrlResponse getPresignedUrl(Authentication authentication,
            @RequestBody GetPresignedUrlRequest request) {
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
        List<FileMetadata> files = fileService.getSharedFiles(user.id());
        Map<String, String> emailsByUserId = fileService.getUserEmails(files.stream()
                .map(FileMetadata::getUploadedByUserId)
                .collect(Collectors.toSet()));
        return files.stream()
                .map(fileMetadata -> new GetFilesResponse(fileMetadata.getId(),
                        fileMetadata.getName(),
                        fileMetadata.getSize(),
                        fileMetadata.getMimeType(),
                        emailsByUserId.getOrDefault(fileMetadata.getUploadedByUserId(),
                                fileMetadata.getUploadedByUserId()),
                        fileMetadata.getStatus().toString()))
                .toList();
    }

    @GetMapping("/{fileId}/shares")
    public List<GetFileSharesResponse> getFileShares(Authentication authentication, @PathVariable String fileId) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        return fileService.getFileShares(user.id(), fileId)
                .stream()
                .map(u -> new GetFileSharesResponse(u.getId(), u.getEmail()))
                .toList();
    }

    @PostMapping("/{fileId}/share")
    public void shareFile(Authentication authentication,
            @PathVariable String fileId,
            @RequestBody ShareFileRequest request) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        fileService.shareFile(user.id(), fileId, request.email());
    }

    @DeleteMapping("/{fileId}/share")
    public void unshareFile(Authentication authentication,
            @PathVariable String fileId,
            @RequestBody UnshareFileRequest request) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        fileService.unshareFile(user.id(), fileId, request.email());
    }

    @PostMapping("/exists")
    public FileExistsResponse exists(Authentication authentication, @RequestBody FileExistsRequest request) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        Optional<FileMetadata> fileOpt = fileService.exists(user.id(), request.filename(), request.fingerprint());
        return new FileExistsResponse(fileOpt.isPresent(),
                fileOpt.map(FileMetadata::getId),
                fileOpt.map(f -> f.getStatus().toString()));
    }

    @PostMapping("/multipart-upload")
    public InitiateMultipartUploadResponse initiateMultipartUpload(Authentication authentication,
            @RequestBody InitiateMultipartUploadRequest request) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        return fileService.initiateMultipartUpload(user.id(),
                request.filename(),
                request.mimeType(),
                request.size(),
                request.fingerprint(),
                request.numChunks());
    }

    @PostMapping("/multipart-upload/presigned-url")
    public GetPresignedUrlMultipartResponse getPresignedUrlForMultipartUpload(Authentication authentication,
            @RequestBody GetPresignedUrlMultipartRequest request) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        return new GetPresignedUrlMultipartResponse(fileService.presignUploadPart(user.id(),
                request.fileId(),
                request.partNumber()));
    }

    @PatchMapping("/multipart-upload")
    public void patchMultipartUpload(Authentication authentication, @RequestBody PatchMultipartUploadRequest request) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        fileService.patchMultipartUpload(user.id(),
                request.fileId(),
                request.partNumber(),
                request.fingerprint(),
                request.etag());
    }

    @PostMapping("/multipart-upload/complete")
    public void completeMultipartUpload(Authentication authentication,
            @RequestBody CompleteMultipartUploadRequest request) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        fileService.completeMultipartUpload(user.id(), request.fileId());
    }

    @GetMapping("/multipart-upload/{fileId}/parts")
    public List<GetPartsResponseItem> getParts(Authentication authentication, @PathVariable String fileId) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        return fileService.getParts(user.id(), fileId)
                .stream()
                .map(fileChunk -> new GetPartsResponseItem(fileChunk.getPartNumber(),
                        fileChunk.getFileChunkStatus().toString(),
                        fileChunk.getFingerprint()))
                .toList();
    }

}
