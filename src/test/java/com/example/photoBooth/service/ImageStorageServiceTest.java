package com.example.photoBooth.service;

import com.example.photoBooth.config.R2Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ImageStorageServiceTest {
    
    @Mock 
    private S3Client s3Client;

    private ImageStorageService imageStorageService;

    @Test 
    void uploadShouldStoreUnderOwnerPrefixedKeyAndReturnIt(){
        R2Properties properties = new R2Properties();
        properties.setBucketName("test-bucket");

        imageStorageService = new ImageStorageService(s3Client, properties);

        UUID ownerId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        UUID storageId = UUID.randomUUID();
        byte[] bytes = "jpeg-bytes".getBytes();

        String key = imageStorageService.upload(ownerId, albumId, storageId, bytes);

        assertEquals("users/" + ownerId + "/albums/" + albumId + "/" + storageId + ".jpg", key);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertEquals("test-bucket", requestCaptor.getValue().bucket());
        assertEquals(key, requestCaptor.getValue().key());
        assertTrue(requestCaptor.getValue().contentType().equals("image/jpeg"));
    }

    @Test 
    void deleteShouldRemoveObjectKey() {
        R2Properties properties = new R2Properties();
        properties.setBucketName("test-bucket");

        imageStorageService = new ImageStorageService(s3Client, properties);

        imageStorageService.delete("users/owner/albums/album/storage.jpg");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertEquals("test-bucket", requestCaptor.getValue().bucket());
        assertEquals("users/owner/albums/album/storage.jpg", requestCaptor.getValue().key());
    }

}
