package com.dropboxclone.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sqs")
public record SqsProperties(
        String endpoint,
        String queueUrl,
        String accessKey,
        String secretKey,
        String region
) {}