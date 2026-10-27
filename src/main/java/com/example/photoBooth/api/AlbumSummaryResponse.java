package com.example.photoBooth.api;

import java.util.UUID;

public record AlbumSummaryResponse(
                UUID id,
                String albumName,
                String cityName,
                String regionName,
                String regionCode,
                String countryName,
                Double lat,
                Double lang,
                int imageCount,
                String coverUrl) {

}
