package com.dropboxclone.backend.health;

import com.dropboxclone.backend.config.StorageProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final StorageProperties storageProperties;

    public HealthController(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "dropbox-clone-backend",
            "storageBucket", storageProperties.bucket()
        );
    }
}
