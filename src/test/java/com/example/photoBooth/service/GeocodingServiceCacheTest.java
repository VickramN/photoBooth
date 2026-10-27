package com.example.photoBooth.service;

import com.example.photoBooth.config.NominatimProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringJUnitConfig(GeocodingServiceCacheTest.TestConfig.class)
class GeocodingServiceCacheTest {

    private static final String PARIS = """
            [{
              "lat": "48.8534951", "lon": "2.3483915",
              "addresstype": "city", "name": "Paris",
              "display_name": "Paris, Ile-de-France, Metropolitan France, France",
              "address": { "city": "Paris", "state": "Ile-de-France", "ISO3166-2-lvl4": "FR-IDF", "country": "France" }
            }]
            """;

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        RestClient.Builder nominatimBuilder() {
            return RestClient.builder().baseUrl("https://nominatim.openstreetmap.org");
        }

        @Bean
        MockRestServiceServer mockServer(RestClient.Builder nominatimBuilder) {
            return MockRestServiceServer.bindTo(nominatimBuilder).build();
        }

        @Bean
        GeocodingService geocodingService(RestClient.Builder nominatimBuilder, MockRestServiceServer mockServer) {
            NominatimProperties properties = new NominatimProperties();
            properties.setMinIntervalMs(0L);
            return new GeocodingService(nominatimBuilder.build(), properties);
        }

        @Bean
        CacheManager cacheManager() {
            return new CaffeineCacheManager("locationSearch");
        }
    }

    @Autowired
    private GeocodingService geocodingService;

    @Autowired
    private MockRestServiceServer mockServer;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        mockServer.reset();
        cacheManager.getCache("locationSearch").clear();
    }

    @Test
    void repeatedSearchShouldCallNominatimOnce() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess(PARIS, MediaType.APPLICATION_JSON));

        geocodingService.search("Paris", null, "France");
        geocodingService.search("Paris", null, "France");

        mockServer.verify();
    }

    @Test
    void searchShouldIgnoreCaseWhenCaching() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess(PARIS, MediaType.APPLICATION_JSON));

        geocodingService.search("Paris", null, "France");
        geocodingService.search("PARIS", null, "france");

        mockServer.verify();
    }

    @Test
    void differentPlacesShouldEachCallNominatim() {
        mockServer.expect(requestTo(containsString("city=Paris")))
                .andRespond(withSuccess(PARIS, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("city=Lyon")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        geocodingService.search("Paris", null, "France");
        geocodingService.search("Lyon", null, "France");

        mockServer.verify();
    }

    @Test
    void sameCityWithDifferentRegionShouldEachCallNominatim() {
        mockServer.expect(requestTo(not(containsString("state="))))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("state=Missouri")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        geocodingService.search("Springfield", null, "United States");
        geocodingService.search("Springfield", "Missouri", "United States");

        mockServer.verify();
    }

    @Test
    void failedSearchShouldNotBeCached() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withServerError());
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess(PARIS, MediaType.APPLICATION_JSON));

        assertThrows(GeocodingUnavailableException.class,
                () -> geocodingService.search("Paris", null, "France"));
        assertEquals(1, geocodingService.search("Paris", null, "France").size());

        mockServer.verify();
    }
}
