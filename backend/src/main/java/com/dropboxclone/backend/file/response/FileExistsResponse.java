package com.dropboxclone.backend.file.response;


import java.util.Optional;

public record FileExistsResponse(boolean exists, Optional<String> fileId, Optional<String> status) {
}
