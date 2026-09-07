package com.dropboxclone.backend.file.controller;


import com.dropboxclone.backend.auth.security.AuthenticatedUser;
import com.dropboxclone.backend.file.request.UploadFileRequest;
import com.dropboxclone.backend.file.response.GetFilesResponse;
import com.dropboxclone.backend.file.response.UploadFileResponse;
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
                                fileMetadata.getUrl()
                        )
                )
                .toList();
    }

    @PostMapping
    public UploadFileResponse uploadFile(Authentication authentication, @RequestBody UploadFileRequest uploadFileRequest) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        assert user != null;
        return new UploadFileResponse(fileService.uploadFile(user.id(), uploadFileRequest));
    }

}
