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
import com.dropboxclone.backend.file.model.SharedFile;
import com.dropboxclone.backend.file.repository.FileMetadataRepository;
import com.dropboxclone.backend.file.repository.SharedFileRepository;
import com.dropboxclone.backend.file.request.GetPresignedUrlRequest;
import com.dropboxclone.backend.file.response.GetPresignedUrlResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.dropboxclone.backend.user.model.User;
import com.dropboxclone.backend.user.repository.UserRepository;
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
    private static final String RECIPIENT_EMAIL = "bob@example.com";

    private final FileMetadataRepository repository = mock(FileMetadataRepository.class);
    private final SharedFileRepository sharedFileRepository = mock(SharedFileRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final S3Service s3Service = mock(S3Service.class);
    private final FileService fileService = new FileService(repository,
            s3Service,
            sharedFileRepository,
            userRepository);
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

        assertThat(fileService.getFiles(USER_ID)).extracting(FileMetadata::getStatus)
                .containsExactly(FileUploadStatus.PENDING, FileUploadStatus.COMPLETED, FileUploadStatus.FAILED);
    }

    @Test
    void getPresignedUrlRejectsInvalidSize() {
        assertBadRequest(new GetPresignedUrlRequest("photo.png", 0L, "image/png"));
        assertBadRequest(new GetPresignedUrlRequest("photo.png", 50L * 1024 * 1024 + 1, "image/png"));
    }

    @Test
    void getPresignedUrlRejectsUnsupportedType() {
        assertBadRequest(new GetPresignedUrlRequest("notes.txt", 100L, "text/plain"));
    }

    @Test
    void getPresignedUrlCreatesPendingMetadata() {
        when(s3Service.presignPutUrl(anyString(), eq("image/png"), eq(FileService.PRESIGN_TTL))).thenReturn(
                "https://example.test/upload");

        GetPresignedUrlResponse response = fileService.getPresignedUrlForUpload(USER_ID,
                new GetPresignedUrlRequest("photo.png", 1024L, "image/png"));

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

        assertThatThrownBy(() -> fileService.getDownloadLink(USER_ID,
                FILE_ID)).isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getDownloadLinkRejectsOtherUser() {
        FileMetadata file = metadata(FileUploadStatus.COMPLETED);
        file.setUploadedByUserId(OTHER_USER_ID);
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.getDownloadLink(USER_ID,
                FILE_ID)).isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(sharedFileRepository).findByUserIdAndFileId(USER_ID, FILE_ID);
    }

    @Test
    void getDownloadLinkRejectsIncompleteFile() {
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(metadata(FileUploadStatus.PENDING)));

        assertThatThrownBy(() -> fileService.getDownloadLink(USER_ID,
                FILE_ID)).isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteFileRejectsOtherUser() {
        FileMetadata file = metadata(FileUploadStatus.COMPLETED);
        file.setUploadedByUserId(OTHER_USER_ID);
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.deleteFile(USER_ID, FILE_ID)).isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(s3Service, never()).deleteObject(anyString());
        verify(sharedFileRepository, never()).deleteByFileId(anyString());
    }

    @Test
    void deleteFileRemovesShareRows() {
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(metadata(FileUploadStatus.COMPLETED)));

        fileService.deleteFile(USER_ID, FILE_ID);

        verify(s3Service).deleteObject(STORAGE_KEY);
        verify(sharedFileRepository).deleteByFileId(FILE_ID);
        verify(repository).delete(any(FileMetadata.class));
    }

    @Test
    void getDownloadLinkAllowsRecipient() {
        FileMetadata file = metadata(FileUploadStatus.COMPLETED);
        file.setUploadedByUserId(USER_ID);
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));
        when(sharedFileRepository.findByUserIdAndFileId(OTHER_USER_ID,
                FILE_ID)).thenReturn(Optional.of(SharedFile.builder().userId(OTHER_USER_ID).fileId(FILE_ID).build()));
        when(s3Service.presignGetUrl(STORAGE_KEY, FileService.PRESIGN_TTL)).thenReturn("https://example.test/download");

        assertThat(fileService.getDownloadLink(OTHER_USER_ID, FILE_ID)).isEqualTo("https://example.test/download");
    }

    @Test
    void getSharedFilesReturnsOnlyCompleted() {
        FileMetadata completed = metadata(FileUploadStatus.COMPLETED);
        FileMetadata pending = metadata(FileUploadStatus.PENDING);
        pending.setId("file-pending");
        when(sharedFileRepository.findByUserId(OTHER_USER_ID)).thenReturn(List.of(SharedFile.builder()
                .userId(OTHER_USER_ID)
                .fileId(FILE_ID)
                .build(), SharedFile.builder().userId(OTHER_USER_ID).fileId("file-pending").build()));
        when(repository.findAllById(List.of(FILE_ID, "file-pending"))).thenReturn(List.of(completed, pending));

        assertThat(fileService.getSharedFiles(OTHER_USER_ID)).extracting(FileMetadata::getId).containsExactly(FILE_ID);
    }

    @Test
    void getUserEmailsReturnsUploaderEmail() {
        when(userRepository.findAllById(List.of(USER_ID))).thenReturn(List.of(owner()));

        assertThat(fileService.getUserEmails(List.of(USER_ID))).containsEntry(USER_ID, "alice@example.com");
    }

    @Test
    void shareFileCreatesShareForRecipient() {
        when(userRepository.findByEmail(RECIPIENT_EMAIL)).thenReturn(Optional.of(recipient()));
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(metadata(FileUploadStatus.COMPLETED)));
        when(sharedFileRepository.findByUserIdAndFileId(OTHER_USER_ID, FILE_ID)).thenReturn(Optional.empty());

        fileService.shareFile(USER_ID, FILE_ID, "  Bob@Example.com  ");

        ArgumentCaptor<SharedFile> captor = ArgumentCaptor.forClass(SharedFile.class);
        verify(sharedFileRepository).save(captor.capture());
        SharedFile stored = captor.getValue();
        assertThat(stored.getUserId()).isEqualTo(OTHER_USER_ID);
        assertThat(stored.getFileId()).isEqualTo(FILE_ID);
        assertThat(stored.getSharedByUserId()).isEqualTo(USER_ID);
        assertThat(stored.getCreatedAt()).isNotNull();
    }

    @Test
    void shareFileIsIdempotent() {
        when(userRepository.findByEmail(RECIPIENT_EMAIL)).thenReturn(Optional.of(recipient()));
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(metadata(FileUploadStatus.COMPLETED)));
        when(sharedFileRepository.findByUserIdAndFileId(OTHER_USER_ID,
                FILE_ID)).thenReturn(Optional.of(SharedFile.builder().userId(OTHER_USER_ID).fileId(FILE_ID).build()));

        fileService.shareFile(USER_ID, FILE_ID, RECIPIENT_EMAIL);

        verify(sharedFileRepository, never()).save(any(SharedFile.class));
    }

    @Test
    void shareFileRejectsUnknownEmail() {
        when(userRepository.findByEmail(RECIPIENT_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.shareFile(USER_ID, FILE_ID, RECIPIENT_EMAIL)).isInstanceOf(
                        ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(sharedFileRepository, never()).save(any());
    }

    @Test
    void shareFileRejectsMissingFile() {
        when(userRepository.findByEmail(RECIPIENT_EMAIL)).thenReturn(Optional.of(recipient()));
        when(repository.findById(FILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.shareFile(USER_ID, FILE_ID, RECIPIENT_EMAIL)).isInstanceOf(
                        ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shareFileRejectsNonOwner() {
        when(userRepository.findByEmail(RECIPIENT_EMAIL)).thenReturn(Optional.of(recipient()));
        FileMetadata file = metadata(FileUploadStatus.COMPLETED);
        file.setUploadedByUserId("someone-else");
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.shareFile(USER_ID, FILE_ID, RECIPIENT_EMAIL)).isInstanceOf(
                        ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(sharedFileRepository, never()).save(any());
    }

    @Test
    void shareFileRejectsSelfShare() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(owner()));
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(metadata(FileUploadStatus.COMPLETED)));

        assertThatThrownBy(() -> fileService.shareFile(USER_ID, FILE_ID, "alice@example.com")).isInstanceOf(
                        ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(sharedFileRepository, never()).save(any());
    }

    @Test
    void shareFileRejectsPendingFile() {
        when(userRepository.findByEmail(RECIPIENT_EMAIL)).thenReturn(Optional.of(recipient()));
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(metadata(FileUploadStatus.PENDING)));

        assertThatThrownBy(() -> fileService.shareFile(USER_ID, FILE_ID, RECIPIENT_EMAIL)).isInstanceOf(
                        ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(sharedFileRepository, never()).save(any());
    }

    @Test
    void getFileSharesRejectsNonOwner() {
        FileMetadata file = metadata(FileUploadStatus.COMPLETED);
        file.setUploadedByUserId(USER_ID);
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.getFileShares(OTHER_USER_ID,
                FILE_ID)).isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unshareFileRemovesShare() {
        when(userRepository.findByEmail(RECIPIENT_EMAIL)).thenReturn(Optional.of(recipient()));
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(metadata(FileUploadStatus.COMPLETED)));

        fileService.unshareFile(USER_ID, FILE_ID, RECIPIENT_EMAIL);

        verify(sharedFileRepository).deleteByUserIdAndFileId(OTHER_USER_ID, FILE_ID);
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
        when(repository.findByStatusAndCreatedAtBefore(eq(FileUploadStatus.PENDING), any())).thenReturn(List.of(stale));
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
        when(repository.findByStatusAndCreatedAtBefore(eq(FileUploadStatus.PENDING), any())).thenReturn(List.of(stale));
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
        when(repository.findByStatusAndCreatedAtBefore(eq(FileUploadStatus.PENDING), any())).thenReturn(List.of(stale));

        fileService.expireStalePendingUploads(now);

        assertThat(stale.getStatus()).isEqualTo(FileUploadStatus.FAILED);
        verify(s3Service, never()).objectExists(anyString());
    }

    private void assertBadRequest(GetPresignedUrlRequest request) {
        assertThatThrownBy(() -> fileService.getPresignedUrlForUpload(USER_ID, request)).isInstanceOf(
                        ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private FileMetadata metadata(FileUploadStatus status) {
        return FileMetadata.builder()
                .id(FILE_ID)
                .name("photo.png")
                .size(1024L)
                .mimeType("image/png")
                .uploadedByUserId(USER_ID)
                .storageKey(STORAGE_KEY)
                .status(status)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private User recipient() {
        return User.builder()
                .id(OTHER_USER_ID)
                .email(RECIPIENT_EMAIL)
                .passwordHash("hash")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private User owner() {
        return User.builder()
                .id(USER_ID)
                .email("alice@example.com")
                .passwordHash("hash")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
