package com.dropboxclone.backend.file.repository;

import com.dropboxclone.backend.file.model.SharedFile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SharedFileRepository extends MongoRepository<SharedFile, String> {

    List<SharedFile> findByUserId(String userId);

    List<SharedFile> findByFileIdAndSharedByUserId(String fileId, String sharedByUserId);

    void deleteByUserIdAndFileId(String userId, String fileId);

    Optional<SharedFile> findByUserIdAndFileId(String userId, String fileId);

    void deleteByFileId(String fileId);
}
