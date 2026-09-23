package com.example.photoBooth.service.upload;

import com.example.photoBooth.config.R2Properties;
import com.example.photoBooth.config.UploadProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresignedUrlServiceTest {

    @Mock
    private S3Presigner presigner;

    @Mock
    private PresignedGetObjectRequest presignedRequest;

    private PresignedUrlService service;

    @BeforeEach
    void setUp() {
        R2Properties r2Properties = new R2Properties();
        r2Properties.setBucketName("test-bucket");

        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.getPresignedUrl().setExpiryMinutes(15);

        service = new PresignedUrlService(presigner, r2Properties, uploadProperties);
    }

    @Test
    void shouldGeneratePresignedUrlForObjectKey() throws Exception {
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(presignedRequest.url())
                .thenReturn(new URL("https://example.r2.cloudflarestorage.com/test-bucket/key123?sig=abc"));

        String url = service.generateGetUrl("key123");

        assertEquals("https://example.r2.cloudflarestorage.com/test-bucket/key123?sig=abc", url);
    }
}