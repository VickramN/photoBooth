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
        Bucket bucket = bucketFor(userId);
        return bucket.tryConsume(1);
    }

    // Gives back a token consumed by tryConsumeUploadToken, for failures that
    // are the server's fault (e.g. the AV scanner being unreachable) rather
    // than the user's -- so an infrastructure outage doesn't burn through
    // someone's hourly quota. addTokens won't exceed the bucket's capacity.
    public void refundUploadToken(UUID userId) {
        bucketFor(userId).addTokens(1);
    }

    private Bucket bucketFor(UUID userId) {
        byte[] key = ("upload-rate-limit:" + userId).getBytes(StandardCharsets.UTF_8);
        return proxyManager.builder().build(key, configSupplier());
    }

    private Supplier<BucketConfiguration> configSupplier() {
        int maxPerHour = uploadProperties.getRateLimit().getMaxPerHour();
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(maxPerHour, Refill.intervally(maxPerHour, Duration.ofHours(1))))
                .build();
    }
}