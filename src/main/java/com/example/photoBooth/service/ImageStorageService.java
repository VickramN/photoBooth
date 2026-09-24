package com.example.photoBooth.service;

import com.example.photoBooth.config.R2Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private final S3Client s3Client;
    private final R2Properties r2Properties;

    public ImageStorageService(S3Client s3Client, R2Properties r2Properties) {
        this.s3Client = s3Client;
        this.r2Properties = r2Properties;
    }

    public String upload(UUID ownerId, UUID albumId, UUID storageId, byte[] bytes) {
        String key = buildKey(ownerId, albumId, storageId);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(key)
                .contentType("image/jpeg")
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(bytes));

        log.info("Uploaded image to R2 with key {}", key);
        return key;
    }

    public void delete(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(objectKey)
                .build();

        s3Client.deleteObject(request);
        log.info("Deleted image from R2 with key {}", objectKey);
    }

    private String buildKey(UUID ownerId, UUID albumId, UUID storageId) {
        return "users/" + ownerId + "/albums/" + albumId + "/" + storageId + ".jpg";
    }
}