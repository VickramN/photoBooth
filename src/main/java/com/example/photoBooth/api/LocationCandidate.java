package com.example.photoBooth.api;

public record LocationCandidate(
        String displayName,
        String cityName,
        String countryName,
        double lat,
        double lang) {

}
