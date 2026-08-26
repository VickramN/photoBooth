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

    public String upload(UUID albumId, String originalFileName, String contentType, byte[] bytes) {
        String key = buildKey(albumId, originalFileName);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(bytes));

        String url = buildUrl(key);
        log.info("Uploaded image to R2 with key {}", key);
        return url;
    }

    public void delete(String imageUrl) {
        String key = extractKeyFromUrl(imageUrl);

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(key)
                .build();

        s3Client.deleteObject(request);
        log.info("Deleted image from R2 with key {}", key);
    }

    private String buildKey(UUID albumId, String originalFilename) {
        String extension = extractExtension(originalFilename);
        return "albums/" + albumId + "/" + UUID.randomUUID() + extension;
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex);
    }

    private String buildUrl(String key) {
        return r2Properties.getPublicUrl() + "/" + key;
    }

    private String extractKeyFromUrl(String imageUrl) {
        String prefix = r2Properties.getPublicUrl() + "/";
        return imageUrl.substring(prefix.length());
    }
}
