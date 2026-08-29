package com.dropboxclone.backend.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.dropboxclone.backend.config.StorageProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void returnsApplicationHealthPayload() {
        HealthController controller = new HealthController(
            new StorageProperties(
                "http://localhost:9000",
                "minioadmin",
                "minioadmin",
                "dropbox-clone",
                "us-east-1"
            )
        );

        Map<String, Object> response = controller.health();

        assertThat(response)
            .containsEntry("status", "UP")
            .containsEntry("service", "dropbox-clone-backend")
            .containsEntry("storageBucket", "dropbox-clone");
    }
}
