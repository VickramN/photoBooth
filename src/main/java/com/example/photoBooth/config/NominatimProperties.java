package com.example.photoBooth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geocoding.nominatim")
public class NominatimProperties {

    private String baseUrl;
    private String userAgent;
    private Long connectTimeoutMs;
    private Long readTimeoutMs;
    private Long minIntervalMs;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setuserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(Long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public Long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(Long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public Long getMinIntervalMs() {
        return minIntervalMs;
    }

    public void setMinIntervalMs(Long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

}
