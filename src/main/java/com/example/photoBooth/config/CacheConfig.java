package com.example.photoBooth.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRESIGNED_URLS = "presignedUrls";

    @Bean
    CacheManagerCustomizer<CaffeineCacheManager> presignedUrlCache(UploadProperties uploadProperties) {
        UploadProperties.PresignedUrl settings = uploadProperties.getPresignedUrl();

        if (settings.getCacheMinutes() >= settings.getExpiryMinutes()) {
            throw new IllegalStateException(
                    "upload.presigned-url.cache-minutes must be less than expiry-minutes, or cached URLs could be expired when served");
        }

        return cacheManager -> cacheManager.registerCustomCache(PRESIGNED_URLS,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(settings.getCacheMinutes()))
                        .build());
    }

}
