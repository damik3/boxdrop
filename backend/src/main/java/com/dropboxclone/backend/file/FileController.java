package com.dropboxclone.backend.file;


import com.dropboxclone.backend.auth.security.AuthenticatedUser;
import com.dropboxclone.backend.file.request.UploadFileRequest;
import com.dropboxclone.backend.file.response.GetFilesResponse;
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
                                fileMetadata.id,
                                fileMetadata.name,
                                fileMetadata.size,
                                fileMetadata.mimeType,
                                fileMetadata.uploadedByUserId,
                                fileMetadata.url
                        )
                )
                .toList();
    }

    @PostMapping
    public String uploadFile(Authentication authentication, @RequestBody UploadFileRequest uploadFileRequest) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        assert user != null;
        return fileService.uploadFile(user.id(), uploadFileRequest);
    }

}
