package com.example.photoBooth.service;

import com.example.photoBooth.api.LocationCandidate;
import com.example.photoBooth.config.NominatimProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class GeocodingService {

    private final RestClient restClient;
    private final NominatimProperties properties;
    private volatile Instant lastRequestTime = Instant.EPOCH;

    public GeocodingService(RestClient nominatimRestClient, NominatimProperties properties) {
        this.restClient = nominatimRestClient;
        this.properties = properties;
    }

    public List<LocationCandidate> search(String cityName, String countryName) {
        List<NominatimResult> results;
        try {
            throttle();
            results = restClient.get()
                    .uri(builder -> builder.path("/search")
                            .queryParam("city", cityName)
                            .queryParam("country", countryName)
                            .queryParam("format", "json")
                            .queryParam("addressdetails", 1)
                            .queryParam("accept-language", "en")
                            .queryParam("limit", 5)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NominatimResult>>() {
                    });
        } catch (RestClientException e) {
            throw new GeocodingUnavailableException("location search failed", e);
        }

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        Set<String> seenCandidates = new HashSet<>();

        return results.stream()
                .map(this::toCandidate)
                .flatMap(Optional::stream)
                .filter(candidate -> seenCandidates.add(candidate.displayName()))
                .toList();
    }

    private Optional<LocationCandidate> toCandidate(NominatimResult result) {
        NominatimAddress address = result.address();

        if (address == null || result.lat() == null || result.lon() == null) {
            return Optional.empty();
        }

        String city = address.city() != null ? address.city()
                : address.town() != null ? address.town()
                        : address.village();

        if (city == null || address.country() == null) {
            return Optional.empty();
        }

        return Optional.of(new LocationCandidate(
                result.displayName(),
                city,
                address.country(),
                Double.parseDouble(result.lat()),
                Double.parseDouble(result.lon())));
    }

    private synchronized void throttle() {
        long elapsedMs = Duration.between(lastRequestTime, Instant.now()).toMillis();
        long waitMs = properties.getMinIntervalMs() - elapsedMs;
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime = Instant.now();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimAddress(String city, String town, String village, String country) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimResult(String lat, String lon, @JsonProperty("display_name") String displayName,
            NominatimAddress address) {
    }
}
