package com.example.photoBooth.service;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.photoBooth.api.LocationCandidate;
import com.example.photoBooth.config.NominatimProperties;

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
    void searchShouldMapCityResultToCandidate() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess("""
                        [{
                            "lat": "42.3588336",
                            "lon": "-71.0578303",
                            "display_name": "Boston, Suffolk County, Massachusetts, United States",
                            "address": { "city": "Boston", "country": "United States" }
                        }]
                        """, MediaType.APPLICATION_JSON));

        List<LocationCandidate> result = geocodingService.search("Boston", "United States");

        assertEquals(1, result.size());
        LocationCandidate candidate = result.get(0);
        assertEquals("Boston, Suffolk County, Massachusetts, United States", candidate.displayName());
        assertEquals("Boston", candidate.cityName());
        assertEquals("United States", candidate.countryName());
        assertEquals(42.3588336, candidate.lat());
        assertEquals(-71.0578303, candidate.lang());
        mockServer.verify();
    }

    @Test
    void searchShouldSendAddressDetailsEnglishAndLimit() {
        mockServer.expect(requestTo(containsString("/search")))
                .andExpect(queryParam("format", "json"))
                .andExpect(queryParam("addressdetails", "1"))
                .andExpect(queryParam("accept-language", "en"))
                .andExpect(queryParam("limit", "5"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        geocodingService.search("Paris", "France");

        mockServer.verify();
    }

    @Test
    void searchShouldDropDuplicateDisplayNamesKeepingFirst() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess("""
                        [
                          {
                            "lat": "48.8534951", "lon": "2.3483915",
                            "display_name": "Paris, Ile-de-France, Metropolitan France, France",
                            "address": { "city": "Paris", "country": "France" }
                          },
                          {
                            "lat": "48.8588897", "lon": "2.3200410",
                            "display_name": "Paris, Ile-de-France, Metropolitan France, France",
                            "address": { "city": "Paris", "country": "France" }
                          },
                          {
                            "lat": "33.6617962", "lon": "-95.5555130",
                            "display_name": "Paris, Lamar County, Texas, United States",
                            "address": { "city": "Paris", "country": "United States" }
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<LocationCandidate> result = geocodingService.search("Paris", "France");

        assertEquals(2, result.size());
        assertEquals(48.8534951, result.get(0).lat()); // first copy kept, not the second
        assertEquals("Paris, Lamar County, Texas, United States", result.get(1).displayName());
        mockServer.verify();
    }

    @Test
    void searchShouldFallBackToTownThenVillage() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess("""
                        [
                          {
                            "lat": "42.6289858", "lon": "-78.7376213",
                            "display_name": "Town of Boston, Erie County, New York, United States",
                            "address": { "town": "Town of Boston", "country": "United States" }
                          },
                          {
                            "lat": "39.7415986", "lon": "-84.8512012",
                            "display_name": "Boston, Wayne County, Indiana, United States",
                            "address": { "village": "Boston", "country": "United States" }
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<LocationCandidate> result = geocodingService.search("Boston", "United States");

        assertEquals(2, result.size());
        assertEquals("Town of Boston", result.get(0).cityName());
        assertEquals("Boston", result.get(1).cityName());
        mockServer.verify();
    }

    @Test
    void searchShouldSkipResultsWithoutLocality() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess("""
                        [
                          {
                            "lat": "42.0", "lon": "-71.5",
                            "display_name": "Massachusetts, United States",
                            "address": { "state": "Massachusetts", "country": "United States" }
                          },
                          {
                            "lat": "41.0", "lon": "-72.0",
                            "display_name": "Somewhere With No Address"
                          },
                          {
                            "lat": "42.3588336", "lon": "-71.0578303",
                            "display_name": "Boston, Suffolk County, Massachusetts, United States",
                            "address": { "city": "Boston", "country": "United States" }
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<LocationCandidate> result = geocodingService.search("Boston", "United States");

        assertEquals(1, result.size());
        assertEquals("Boston", result.get(0).cityName());
        mockServer.verify();
    }

    @Test
    void searchShouldReturnEmptyListWhenNoMatches() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<LocationCandidate> result = geocodingService.search("Pariss", "France");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void searchShouldThrowUnavailableWhenServerErrors() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withServerError());

        assertThrows(GeocodingUnavailableException.class,
                () -> geocodingService.search("Paris", "France"));
        mockServer.verify();
    }

    @Test
    void searchShouldThrowUnavailableWhenRateLimited() {
        mockServer.expect(requestTo(containsString("/search")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(GeocodingUnavailableException.class,
                () -> geocodingService.search("Paris", "France"));
        mockServer.verify();
    }

}
