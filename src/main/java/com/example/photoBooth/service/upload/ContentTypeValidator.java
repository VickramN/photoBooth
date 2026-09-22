package com.example.photoBooth.service.upload;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ContentTypeValidator {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Tika tika = new Tika();

    public boolean isAllowedImage(byte[] bytes) {
        String detected = tika.detect(bytes);
        return ALLOWED_TYPES.contains(detected);
    }
}