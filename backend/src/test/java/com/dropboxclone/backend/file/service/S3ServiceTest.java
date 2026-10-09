package com.dropboxclone.backend.file.service;

import com.dropboxclone.backend.config.StorageProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3ServiceTest {
    private final S3Client s3Client = mock(S3Client.class);
    private final S3Service s3Service = new S3Service(mock(S3Presigner.class),
            new StorageProperties(null, null, null, null, "files", null), s3Client);

    @Test
    void objectExistsReturnsFalseForMissingHeadObject() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        assertThat(s3Service.objectExists("missing")).isFalse();
    }

    @Test
    void objectExistsPropagatesStorageFailures() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).build());

        assertThatThrownBy(() -> s3Service.objectExists("unavailable")).isInstanceOf(S3Exception.class);
    }
}
