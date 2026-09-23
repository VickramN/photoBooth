package com.example.photoBooth.service.upload;

import com.example.photoBooth.config.R2Properties;
import com.example.photoBooth.config.UploadProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Service
public class PresignedUrlService {

    private final S3Presigner presigner;
    private final R2Properties r2Properties;
    private final UploadProperties uploadProperties;

    public PresignedUrlService(S3Presigner presigner, R2Properties r2Properties, UploadProperties uploadProperties) {
        this.presigner = presigner;
        this.r2Properties = r2Properties;
        this.uploadProperties = uploadProperties;
    }

    public String generateGetUrl(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(uploadProperties.getPresignedUrl().getExpiryMinutes()))
                .getObjectRequest(getObjectRequest)
                .build();

        return presigner.presignGetObject(presignRequest).url().toString();
    }
}