package com.example.photoBooth.service;

import com.example.photoBooth.config.NominatimProperties;
import com.example.photoBooth.entity.Album;
import com.example.photoBooth.repository.AlbumRepository;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import org.springframework.http.MediaType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GeocodingServiceTest {

    private MockRestServiceServer mockServer;
    private GeocodingService geocodingService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://nominatim.openstreetmap.org");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        NominatimProperties properties = new NominatimProperties();
        properties.setMinIntervalMs(0L); // no throttle delay in tests

        geocodingService = new GeocodingService(restClient, properties);
    }

    @Test
    void geocodeShouldReturnCoordinatesWhenResultFound() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess(
                        "[{\"lat\":\"43.4553\",\"lon\":\"-76.5105\"}]",
                        MediaType.APPLICATION_JSON));

        Optional<GeocodingService.Coordinates> result = geocodingService.geocode("Oswego", "USA");

        assertTrue(result.isPresent());
        assertEquals(43.4553, result.get().lat());
        assertEquals(-76.5105, result.get().lang());
        mockServer.verify();
    }

    @Test
    void geocodeShouldReturnEmptyWhenNoResultsFound() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        Optional<GeocodingService.Coordinates> result = geocodingService.geocode("Nowhere", "Nowhereland");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void geocodeShouldReturnEmptyWhenServerErrors() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withServerError());

        Optional<GeocodingService.Coordinates> result = geocodingService.geocode("Oswego", "USA");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

}
