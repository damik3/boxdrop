package com.dropboxclone.backend.file.repository;

import com.dropboxclone.backend.file.model.FileChunkStatus;
import com.dropboxclone.backend.file.model.FileMetadata;
import com.dropboxclone.backend.file.model.FileUploadStatus;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultipartActivityRepositoryImplTest {
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final MultipartActivityRepository repository = new MultipartActivityRepositoryImpl(mongoTemplate);
    private final Instant now = Instant.parse("2026-10-09T14:00:00Z");

    @Test
    void touchSucceedsWhenTimestampIsAlreadyCurrent() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(FileMetadata.class)))
                .thenReturn(UpdateResult.acknowledged(1, 0L, null));

        assertThat(repository.touchMultipart("file-1", FileUploadStatus.PENDING, now)).isTrue();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(FileMetadata.class));
        assertThat(query.getValue().getQueryObject().get("status")).isEqualTo(FileUploadStatus.PENDING);
        assertThat(query.getValue().getQueryObject().get("_id")).isEqualTo("file-1");
        assertThat(update.getValue().getUpdateObject().containsKey("$max")).isTrue();
    }

    @Test
    void chunkConfirmationSucceedsWhenItMakesNoChange() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(FileMetadata.class)))
                .thenReturn(UpdateResult.acknowledged(1, 0L, null));

        assertThat(repository.markChunkUploaded("file-1", FileUploadStatus.PENDING, 1,
                FileChunkStatus.UPLOADED, "sha256", "etag", now)).isTrue();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateFirst(query.capture(), any(Update.class), eq(FileMetadata.class));
        assertThat(query.getValue().getQueryObject().get("fileChunks.partNumber")).isEqualTo(1);
    }

    @Test
    void activityRejectsUploadsThatNoLongerMatchPendingState() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(FileMetadata.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertThat(repository.touchMultipart("file-1", FileUploadStatus.PENDING, now)).isFalse();
        assertThat(repository.markChunkUploaded("file-1", FileUploadStatus.PENDING, 1,
                FileChunkStatus.UPLOADED, "sha256", "etag", now)).isFalse();
    }
}
