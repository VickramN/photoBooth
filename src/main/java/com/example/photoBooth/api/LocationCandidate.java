package com.example.photoBooth.api;

public record LocationCandidate(
                String displayName,
                String cityName,
                String regionName,
                String regionCode,
                String countryName,
                double lat,
                double lang) {

}
