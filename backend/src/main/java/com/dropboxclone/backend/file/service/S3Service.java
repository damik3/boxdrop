package com.dropboxclone.backend.file.service;

import com.dropboxclone.backend.config.StorageProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
public class S3Service {

    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;
    private final S3Client s3Client;

    public S3Service(S3Presigner s3Presigner, StorageProperties storageProperties, S3Client s3Client) {
        this.s3Presigner = s3Presigner;
        this.storageProperties = storageProperties;
        this.s3Client = s3Client;
    }

    public PresignedPutObjectRequest presignPut(String key, String contentType, Duration ttl) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(storageProperties.bucket())
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest putObjectPresignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(putObjectRequest)
                .build();

        return s3Presigner.presignPutObject(putObjectPresignRequest);
    }

    public boolean objectExists(String key) {
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(storageProperties.bucket())
                            .key(key)
                            .build()
            );
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        }
    }

}
