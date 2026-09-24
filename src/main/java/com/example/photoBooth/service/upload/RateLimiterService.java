package com.example.photoBooth.service.upload;

import com.example.photoBooth.config.UploadProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class RateLimiterService {

    private final ProxyManager<byte[]> proxyManager;
    private final UploadProperties uploadProperties;

    public RateLimiterService(@Lazy ProxyManager<byte[]> proxyManager, UploadProperties uploadProperties) {
        this.proxyManager = proxyManager;
        this.uploadProperties = uploadProperties;
    }

    public boolean tryConsumeUploadToken(UUID userId) {
        byte[] key = ("upload-rate-limit:" + userId).getBytes(StandardCharsets.UTF_8);
        Bucket bucket = proxyManager.builder().build(key, configSupplier());
        return bucket.tryConsume(1);
    }

    private Supplier<BucketConfiguration> configSupplier() {
        int maxPerHour = uploadProperties.getRateLimit().getMaxPerHour();
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(maxPerHour, Refill.intervally(maxPerHour, Duration.ofHours(1))))
                .build();
    }
}