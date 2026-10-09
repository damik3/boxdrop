package com.dropboxclone.backend.file.repository;

import com.dropboxclone.backend.file.model.FileChunkStatus;
import com.dropboxclone.backend.file.model.FileMetadata;
import com.dropboxclone.backend.file.model.FileUploadStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

public class MultipartActivityRepositoryImpl implements MultipartActivityRepository {
    private final MongoTemplate mongoTemplate;

    public MultipartActivityRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public boolean touchMultipart(String fileId, FileUploadStatus status, Instant now) {
        Query query = new Query(Criteria.where("_id").is(fileId)
                .and("status").is(status)
                .and("s3UploadId").ne(null));
        return mongoTemplate.updateFirst(query, new Update().max("updatedAt", now),
                FileMetadata.class).getMatchedCount() != 0;
    }

    @Override
    public boolean markChunkUploaded(String fileId, FileUploadStatus pending, Integer partNumber,
            FileChunkStatus status, String fingerprint, String etag, Instant now) {
        Query query = new Query(Criteria.where("_id").is(fileId)
                .and("status").is(pending)
                .and("s3UploadId").ne(null)
                .and("fileChunks.partNumber").is(partNumber));
        Update update = new Update()
                .set("fileChunks.$.fileChunkStatus", status)
                .set("fileChunks.$.fingerprint", fingerprint)
                .set("fileChunks.$.etag", etag)
                .max("updatedAt", now);
        return mongoTemplate.updateFirst(query, update, FileMetadata.class).getMatchedCount() != 0;
    }
}
