package com.example.photoBooth.service;

import com.example.photoBooth.api.LocationCandidate;
import com.example.photoBooth.config.NominatimProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class GeocodingService {

    private static final Set<String> SETTLEMENT_TYPES = Set.of("city", "town", "village");

    private final RestClient restClient;
    private final NominatimProperties properties;
    private volatile Instant lastRequestTime = Instant.EPOCH;

    public GeocodingService(RestClient nominatimRestClient, NominatimProperties properties) {
        this.restClient = nominatimRestClient;
        this.properties = properties;
    }

    @Cacheable(cacheNames = "locationSearch", sync = true, key = "T(com.example.photoBooth.service.GeocodingService).cacheKey(#cityName, #region, #countryName)")
    public List<LocationCandidate> search(String cityName, String region, String countryName) {
        List<NominatimResult> results;
        try {
            throttle();
            results = restClient.get()
                    .uri(builder -> builder.path("/search")
                            .queryParam("city", cityName)
                            .queryParamIfPresent("state", Optional.ofNullable(region))
                            .queryParam("country", countryName)
                            .queryParam("format", "jsonv2")
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

        Set<String> seenLabels = new HashSet<>();
        Set<String> seenCoordinates = new HashSet<>();

        return results.stream()
                .map(this::toCandidate)
                .flatMap(Optional::stream)
                .filter(candidate -> {
                    String coordinates = String.format(Locale.ROOT, "%.4f,%.4f", candidate.lat(), candidate.lang());
                    boolean newLabel = seenLabels.add(candidate.displayName());
                    boolean newCoordinates = seenCoordinates.add(coordinates);
                    return newLabel && newCoordinates;
                })
                .toList();
    }

    private Optional<LocationCandidate> toCandidate(NominatimResult result) {
        NominatimAddress address = result.address();

        if (address == null
                || result.lat() == null
                || result.lon() == null
                || result.name() == null
                || result.addresstype() == null
                || address.country() == null) {
            return Optional.empty();
        }

        if (!SETTLEMENT_TYPES.contains(result.addresstype())) {
            return Optional.empty();
        }

        String regionName = address.state() != null ? address.state() : address.province();

        return Optional.of(new LocationCandidate(
                result.displayName(),
                result.name(),
                regionName,
                address.regionCode(),
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

    public static String cacheKey(String city, String region, String country) {
        String normalizedRegion = region == null ? "" : region.toLowerCase(Locale.ROOT);
        return city.toLowerCase(Locale.ROOT) + "|" + normalizedRegion + "|" + country.toLowerCase(Locale.ROOT);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimAddress(
            String state,
            String province,
            @JsonProperty("ISO3166-2-lvl4") String regionCode,
            String country) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimResult(String lat, String lon,
            @JsonProperty("display_name") String displayName,
            String name,
            String addresstype,
            NominatimAddress address) {
    }
}
