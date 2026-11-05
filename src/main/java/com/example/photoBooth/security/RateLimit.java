package com.example.photoBooth.security;

import java.time.Duration;

public enum RateLimit {

    LOGIN_IP("login-ip", 20, Duration.ofMinutes(15)),
    LOGIN_FAILURES_USERNAME("login-fail-user", 5, Duration.ofMinutes(15)),
    FORGOT_PASSWORD_IP("forgot-ip", 5, Duration.ofHours(1)),
    FORGOT_PASSWORD_USERNAME("forgot-user", 3, Duration.ofHours(1)),
    REGISTER_IP("register-ip", 5, Duration.ofHours(1)),
    LOCATION_SEARCH_USER("location-search-user", 30, Duration.ofMinutes(1));

    private final String keyPrefix;
    private final int capacity;
    private final Duration period;

    RateLimit(String keyPrefix, int capacity, Duration period) {
        this.keyPrefix = keyPrefix;
        this.capacity = capacity;
        this.period = period;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    public int capacity() {
        return capacity;
    }

    public Duration period() {
        return period;
    }
}
