package com.example.photoBooth.service.upload;

import com.example.photoBooth.config.UploadProperties;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private ProxyManager<byte[]> proxyManager;

    @Mock
    private RemoteBucketBuilder<byte[]> bucketBuilder;

    @Mock
    private BucketProxy bucket;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        UploadProperties properties = new UploadProperties();
        properties.getRateLimit().setMaxPerHour(20);
        rateLimiterService = new RateLimiterService(proxyManager, properties);
    }

    @Test
    void shouldAllowWhenBucketHasTokens() {
        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(any(byte[].class), any(Supplier.class))).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        assertTrue(rateLimiterService.tryConsumeUploadToken(UUID.randomUUID()));
    }

    @Test
    void shouldDenyWhenBucketExhausted() {
        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(any(byte[].class), any(Supplier.class))).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(false);

        assertFalse(rateLimiterService.tryConsumeUploadToken(UUID.randomUUID()));
    }
}