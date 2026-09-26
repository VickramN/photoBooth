package com.example.photoBooth.api;

import java.util.List;
import java.util.UUID;

public record AlbumResponse(
        UUID id,
        String albumName,
        String cityName,
        String countryName,
        Double lat,
        Double lang,
        List<ImageResponse> images) {
}
