package com.example.photoBooth.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class RequestRateLimiter {

    private final ProxyManager<byte[]> proxyManager;

    public RequestRateLimiter(@Lazy ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    /** Spends one token, or throws if the bucket is empty. */
    public void consume(RateLimit limit, String id) {
        ConsumptionProbe probe = bucket(limit, id).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitedException(toSeconds(probe.getNanosToWaitForRefill()));
        }
    }

    /** Throws if the bucket is empty, without spending a token. */
    public void requireAvailable(RateLimit limit, String id) {
        EstimationProbe probe = bucket(limit, id).estimateAbilityToConsume(1);
        if (!probe.canBeConsumed()) {
            throw new RateLimitedException(toSeconds(probe.getNanosToWaitForRefill()));
        }
    }

    /** Spends one token if any are left; never throws. */
    public void recordFailure(RateLimit limit, String id) {
        bucket(limit, id).tryConsume(1);
    }

    private Bucket bucket(RateLimit limit, String id) {
        byte[] key = ("rate-limit:" + limit.keyPrefix() + ":" + id).getBytes(StandardCharsets.UTF_8);
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.capacity())
                        .refillIntervally(limit.capacity(), limit.period())
                        .build())
                .build();
        return proxyManager.builder().build(key, () -> configuration);
    }

    // Round up, so a wait of 0.3 seconds is reported as 1 rather than 0.
    private static long toSeconds(long nanos) {
        return Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
    }
}