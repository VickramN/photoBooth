package com.example.photoBooth.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SessionCookies {

    public static final String NAME = "PB_SESSION";

    private final JwtProperties jwtProperties;

    public SessionCookies(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public ResponseCookie create(String token) {
        return base(token)
                .maxAge(Duration.ofMillis(jwtProperties.getExpirationMs()))
                .build();
    }

    public ResponseCookie clear() {

        return base("")
                .maxAge(Duration.ZERO)
                .build();

    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/");
    }

}
