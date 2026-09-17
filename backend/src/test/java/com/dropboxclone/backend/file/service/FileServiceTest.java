package com.dropboxclone.backend.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dropboxclone.backend.file.model.FileMetadata;
import com.dropboxclone.backend.file.model.FileUploadStatus;
import com.dropboxclone.backend.file.repository.FileMetadataRepository;
import com.dropboxclone.backend.file.request.GetPresignedUrlRequest;
import com.dropboxclone.backend.file.response.GetPresignedUrlResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FileServiceTest {

    private static final String USER_ID = "user-1";
    private static final String OTHER_USER_ID = "user-2";
    private static final String FILE_ID = "file-1";
    private static final String STORAGE_KEY = "users/user-1/file-1-photo.png";

    private final FileMetadataRepository repository = mock(FileMetadataRepository.class);
    private final S3Service s3Service = mock(S3Service.class);
    private final FileService fileService = new FileService(repository, s3Service);
    private final AtomicInteger idSequence = new AtomicInteger();

    @BeforeEach
    void stubSaveAssignsId() {
        when(repository.save(any(FileMetadata.class))).thenAnswer(invocation -> {
            FileMetadata file = invocation.getArgument(0);
            if (file.getId() == null) {
                file.setId("file-" + idSequence.incrementAndGet());
            }
            return file;
        });
    }

    @Test
    void getFilesIncludesPendingCompletedAndFailed() {
        FileMetadata pending = metadata(FileUploadStatus.PENDING);
        FileMetadata completed = metadata(FileUploadStatus.COMPLETED);
        FileMetadata failed = metadata(FileUploadStatus.FAILED);
        when(repository.findAllByUploadedByUserId(USER_ID)).thenReturn(List.of(pending, completed, failed));

        assertThat(fileService.getFiles(USER_ID))
                .extracting(FileMetadata::getStatus)
                .containsExactly(FileUploadStatus.PENDING, FileUploadStatus.COMPLETED, FileUploadStatus.FAILED);
    }

    @Test
    void getPresignedUrlRejectsInvalidSize() {
        assertBadRequest(new GetPresignedUrlRequest("photo.png", 0, "image/png"));
        assertBadRequest(new GetPresignedUrlRequest("photo.png", 50 * 1024 * 1024 + 1, "image/png"));
    }

    @Test
    void getPresignedUrlRejectsUnsupportedType() {
        assertBadRequest(new GetPresignedUrlRequest("notes.txt", 100, "text/plain"));
    }

    @Test
    void getPresignedUrlCreatesPendingMetadata() {
        when(s3Service.presignPutUrl(anyString(), eq("image/png"), eq(FileService.PRESIGN_TTL)))
                .thenReturn("https://example.test/upload");

        GetPresignedUrlResponse response = fileService.getPresignedUrlForUpload(
                USER_ID,
                new GetPresignedUrlRequest("photo.png", 1024, "image/png")
        );

        assertThat(response.fileId()).isNotBlank();
        assertThat(response.presignedUrl()).isEqualTo("https://example.test/upload");

        ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
        verify(repository, times(2)).save(captor.capture());
        FileMetadata stored = captor.getValue();
        assertThat(stored.getStatus()).isEqualTo(FileUploadStatus.PENDING);
        assertThat(stored.getStorageKey()).isEqualTo("users/%s/%s-photo.png".formatted(USER_ID, stored.getId()));
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
    }

    @Test
    void getDownloadLinkRejectsMissingFile() {
        when(repository.findById(FILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getDownloadLink(USER_ID, FILE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getDownloadLinkRejectsOtherUser() {
        FileMetadata file = metadata(FileUploadStatus.COMPLETED);
        file.setUploadedByUserId(OTHER_USER_ID);
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.getDownloadLink(USER_ID, FILE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getDownloadLinkRejectsIncompleteFile() {
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(metadata(FileUploadStatus.PENDING)));

        assertThatThrownBy(() -> fileService.getDownloadLink(USER_ID, FILE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteFileRejectsOtherUser() {
        FileMetadata file = metadata(FileUploadStatus.COMPLETED);
        file.setUploadedByUserId(OTHER_USER_ID);
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.deleteFile(USER_ID, FILE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(s3Service, never()).deleteObject(anyString());
    }

    @Test
    void completeByStorageKeyIsIdempotentAndIgnoresUnknownKeys() {
        when(repository.findByStorageKey("missing")).thenReturn(Optional.empty());
        fileService.completeByStorageKey("missing");
        verify(repository, never()).save(any());

        FileMetadata completed = metadata(FileUploadStatus.COMPLETED);
        when(repository.findByStorageKey(STORAGE_KEY)).thenReturn(Optional.of(completed));
        fileService.completeByStorageKey(STORAGE_KEY);
        verify(s3Service, never()).objectExists(anyString());
    }

    @Test
    void completeByStorageKeyMarksCompletedWhenObjectExists() {
        FileMetadata pending = metadata(FileUploadStatus.PENDING);
        when(repository.findByStorageKey(STORAGE_KEY)).thenReturn(Optional.of(pending));
        when(s3Service.objectExists(STORAGE_KEY)).thenReturn(true);

        fileService.completeByStorageKey(STORAGE_KEY);

        assertThat(pending.getStatus()).isEqualTo(FileUploadStatus.COMPLETED);
        assertThat(pending.getUpdatedAt()).isNotNull();
        verify(repository).save(pending);
    }

    @Test
    void completeByStorageKeyCanRecoverFailedUploads() {
        FileMetadata failed = metadata(FileUploadStatus.FAILED);
        when(repository.findByStorageKey(STORAGE_KEY)).thenReturn(Optional.of(failed));
        when(s3Service.objectExists(STORAGE_KEY)).thenReturn(true);

        fileService.completeByStorageKey(STORAGE_KEY);

        assertThat(failed.getStatus()).isEqualTo(FileUploadStatus.COMPLETED);
    }

    @Test
    void expireStalePendingCompletesWhenObjectExists() {
        Instant now = Instant.parse("2026-01-01T00:20:00Z");
        FileMetadata stale = metadata(FileUploadStatus.PENDING);
        when(repository.findByStatusAndCreatedAtBefore(eq(FileUploadStatus.PENDING), any()))
                .thenReturn(List.of(stale));
        when(s3Service.objectExists(STORAGE_KEY)).thenReturn(true);

        fileService.expireStalePendingUploads(now);

        assertThat(stale.getStatus()).isEqualTo(FileUploadStatus.COMPLETED);
        assertThat(stale.getUpdatedAt()).isEqualTo(now);
        verify(repository).save(stale);
    }

    @Test
    void expireStalePendingFailsWhenObjectMissing() {
        Instant now = Instant.parse("2026-01-01T00:20:00Z");
        FileMetadata stale = metadata(FileUploadStatus.PENDING);
        when(repository.findByStatusAndCreatedAtBefore(eq(FileUploadStatus.PENDING), any()))
                .thenReturn(List.of(stale));
        when(s3Service.objectExists(STORAGE_KEY)).thenReturn(false);

        fileService.expireStalePendingUploads(now);

        assertThat(stale.getStatus()).isEqualTo(FileUploadStatus.FAILED);
        verify(s3Service).objectExists(STORAGE_KEY);
        verify(repository).save(stale);
    }

    @Test
    void expireStalePendingFailsWhenStorageKeyMissing() {
        Instant now = Instant.parse("2026-01-01T00:20:00Z");
        FileMetadata stale = metadata(FileUploadStatus.PENDING);
        stale.setStorageKey(null);
        when(repository.findByStatusAndCreatedAtBefore(eq(FileUploadStatus.PENDING), any()))
                .thenReturn(List.of(stale));

        fileService.expireStalePendingUploads(now);

        assertThat(stale.getStatus()).isEqualTo(FileUploadStatus.FAILED);
        verify(s3Service, never()).objectExists(anyString());
    }

    private void assertBadRequest(GetPresignedUrlRequest request) {
        assertThatThrownBy(() -> fileService.getPresignedUrlForUpload(USER_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private FileMetadata metadata(FileUploadStatus status) {
        return FileMetadata.builder()
                .id(FILE_ID)
                .name("photo.png")
                .size(1024)
                .mimeType("image/png")
                .uploadedByUserId(USER_ID)
                .storageKey(STORAGE_KEY)
                .status(status)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
