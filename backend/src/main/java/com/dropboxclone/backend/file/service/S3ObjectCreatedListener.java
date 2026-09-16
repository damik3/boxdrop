package com.dropboxclone.backend.file.service;

import com.dropboxclone.backend.config.SqsProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

@Component
public class S3ObjectCreatedListener {
    private final SqsClient sqsClient;
    private final SqsProperties sqsProperties;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    public S3ObjectCreatedListener(SqsClient sqsClient, SqsProperties sqsProperties, FileService fileService, ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.sqsProperties = sqsProperties;
        this.fileService = fileService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        var result = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(sqsProperties.queueUrl())
                .maxNumberOfMessages(10)
                .waitTimeSeconds(20)
                .build());

        for (Message message : result.messages()) {
            try {
                handle(message.body());
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(sqsProperties.queueUrl())
                        .receiptHandle(message.receiptHandle())
                        .build());
            } catch (Exception e) {
                // no delete → visibility timeout → retry → DLQ
                System.err.println("Exception while processing message: " + message.body());
                e.printStackTrace();
            }
        }
    }

    private void handle(String body) throws NoSuchElementException {
        JsonNode records = objectMapper.readTree(body).path("Records");
        for (JsonNode record : records) {
            String encodedKey = record.path("s3").path("object").path("key").asString();
            if (encodedKey.isBlank()) continue;
            String key = URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
            fileService.completeByStorageKey(key);
        }
    }
}
