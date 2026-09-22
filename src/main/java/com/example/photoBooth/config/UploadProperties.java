package com.example.photoBooth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix= "upload")
public class UploadProperties {

    private long maxFileSizeBytes = 20_971_520L;
    private int maxDimensionPx = 4096;
    private final RateLimit rateLimit = new RateLimit();
    private final PresignedUrl presignedUrl = new PresignedUrl();

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes){
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int getMaxDimensionPx(){
        return maxDimensionPx;
    }

    public void setMaxDimensionPx(int maxDimensionPx){
        this.maxDimensionPx = maxDimensionPx;
    }

    public RateLimit getRateLimit(){
        return rateLimit;
    }

    public PresignedUrl getPresignedUrl(){
        return presignedUrl;
    }


    public static class RateLimit {
        private int maxPerHour = 100;

        public int getMaxPerHour(){
            return maxPerHour;
        }

        public void setMaxPerHour(int maxPerHour){
            this.maxPerHour = maxPerHour;
        }
    }
    
    public static class PresignedUrl{
        private int expiryMinutes = 15;

        public int getExpiryMinutes(){
            return expiryMinutes;
        }

        public void setExpiryMinutes(int expiryMinutes){
            this.expiryMinutes = expiryMinutes;
        }
    }

    
}
