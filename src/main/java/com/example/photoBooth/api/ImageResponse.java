package com.example.photoBooth.api;

import java.util.UUID;

public record ImageResponse(UUID id, UUID albumId, String url) {
}
