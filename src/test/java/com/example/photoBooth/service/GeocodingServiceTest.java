package com.example.photoBooth.service;

import com.example.photoBooth.api.LocationCandidate;
import com.example.photoBooth.config.NominatimProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

  private void respondWith(String json) {
    mockServer.expect(requestTo(containsString("/search")))
        .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
  }

  // ---- mapping ----

  @Test
  void searchShouldMapCityResultWithStateRegion() {
    respondWith("""
        [{
          "lat": "42.3588336", "lon": "-71.0578303",
          "addresstype": "city", "name": "Boston",
          "display_name": "Boston, Suffolk County, Massachusetts, United States",
          "address": { "city": "Boston", "county": "Suffolk County", "state": "Massachusetts",
                       "ISO3166-2-lvl4": "US-MA", "country": "United States", "country_code": "us" }
        }]
        """);

    List<LocationCandidate> result = geocodingService.search("Boston", null, "United States");

    assertEquals(1, result.size());
    LocationCandidate candidate = result.get(0);
    assertEquals("Boston, Suffolk County, Massachusetts, United States", candidate.displayName());
    assertEquals("Boston", candidate.cityName());
    assertEquals("Massachusetts", candidate.regionName());
    assertEquals("US-MA", candidate.regionCode());
    assertEquals("United States", candidate.countryName());
    assertEquals(42.3588336, candidate.lat());
    assertEquals(-71.0578303, candidate.lang());
    mockServer.verify();
  }

  @Test
  void searchShouldFallBackToProvinceForRegion() {
    respondWith("""
        [{
          "lat": "35.0115754", "lon": "135.7681441",
          "addresstype": "city", "name": "Kyoto",
          "display_name": "Kyoto, Kyoto Prefecture, Japan",
          "address": { "city": "Kyoto", "province": "Kyoto Prefecture",
                       "ISO3166-2-lvl4": "JP-26", "country": "Japan", "country_code": "jp" }
        }]
        """);

    LocationCandidate candidate = geocodingService.search("Kyoto", null, "Japan").get(0);

    assertEquals("Kyoto Prefecture", candidate.regionName());
    assertEquals("JP-26", candidate.regionCode());
    mockServer.verify();
  }

  @Test
  void searchShouldAllowMissingRegion() {
    respondWith("""
        [{
          "lat": "1.29", "lon": "103.85",
          "addresstype": "city", "name": "Singapore",
          "display_name": "Singapore",
          "address": { "city": "Singapore", "country": "Singapore", "country_code": "sg" }
        }]
        """);

    LocationCandidate candidate = geocodingService.search("Singapore", null, "Singapore").get(0);

    assertNull(candidate.regionName());
    assertNull(candidate.regionCode());
    mockServer.verify();
  }

  @Test
  void searchShouldUseMatchedPlaceNameNotContainingTown() {
    // The village of Boston sits inside the town of New Boston; the city must be
    // "Boston"
    respondWith("""
        [{
          "lat": "33.4415086", "lon": "-94.4196452",
          "addresstype": "village", "name": "Boston",
          "display_name": "Boston, New Boston, Bowie County, Texas, 75570, United States",
          "address": { "village": "Boston", "town": "New Boston", "county": "Bowie County",
                       "state": "Texas", "ISO3166-2-lvl4": "US-TX", "country": "United States" }
        }]
        """);

    LocationCandidate candidate = geocodingService.search("Boston", null, "United States").get(0);

    assertEquals("Boston", candidate.cityName());
    assertEquals("US-TX", candidate.regionCode());
    mockServer.verify();
  }

  // ---- filtering ----

  @Test
  void searchShouldSkipNonSettlementResults() {
    respondWith("""
        [
          {
            "lat": "35.0115754", "lon": "135.7681441",
            "addresstype": "city", "name": "Kyoto",
            "display_name": "Kyoto, Kyoto Prefecture, Japan",
            "address": { "city": "Kyoto", "province": "Kyoto Prefecture",
                         "ISO3166-2-lvl4": "JP-26", "country": "Japan" }
          },
          {
            "lat": "35.0233960", "lon": "135.7632000",
            "addresstype": "neighbourhood", "name": "Kyōto-Gyoen",
            "display_name": "Kyōto-Gyoen, Kamigyō Ward, Kyoto, Kyoto Prefecture, 602-0881, Japan",
            "address": { "neighbourhood": "Kyōto-Gyoen", "city": "Kyoto", "province": "Kyoto Prefecture",
                         "ISO3166-2-lvl4": "JP-26", "country": "Japan" }
          }
        ]
        """);

    List<LocationCandidate> result = geocodingService.search("Kyoto", "Kyoto", "Japan");

    assertEquals(1, result.size());
    assertEquals("Kyoto", result.get(0).cityName());
    mockServer.verify();
  }

  @Test
  void searchShouldSkipResultsMissingRequiredFields() {
    respondWith("""
        [
          {
            "lat": "41.0", "lon": "-72.0",
            "addresstype": "city", "name": "No Address",
            "display_name": "No Address"
          },
          {
            "lat": "41.1", "lon": "-72.1",
            "name": "No Type",
            "display_name": "No Type, United States",
            "address": { "country": "United States" }
          },
          {
            "lat": "41.2", "lon": "-72.2",
            "addresstype": "city",
            "display_name": "No Name, United States",
            "address": { "country": "United States" }
          },
          {
            "lat": "42.3588336", "lon": "-71.0578303",
            "addresstype": "city", "name": "Boston",
            "display_name": "Boston, Suffolk County, Massachusetts, United States",
            "address": { "state": "Massachusetts", "ISO3166-2-lvl4": "US-MA", "country": "United States" }
          }
        ]
        """);

    List<LocationCandidate> result = geocodingService.search("Boston", null, "United States");

    assertEquals(1, result.size());
    assertEquals("Boston", result.get(0).cityName());
    mockServer.verify();
  }

  // ---- dedupe ----

  @Test
  void searchShouldDropDuplicateLabelsKeepingFirst() {
    respondWith("""
        [
          {
            "lat": "48.8534951", "lon": "2.3483915",
            "addresstype": "city", "name": "Paris",
            "display_name": "Paris, Ile-de-France, Metropolitan France, France",
            "address": { "city": "Paris", "state": "Ile-de-France", "ISO3166-2-lvl4": "FR-IDF", "country": "France" }
          },
          {
            "lat": "48.8588897", "lon": "2.3200410",
            "addresstype": "city", "name": "Paris",
            "display_name": "Paris, Ile-de-France, Metropolitan France, France",
            "address": { "city": "Paris", "state": "Ile-de-France", "ISO3166-2-lvl4": "FR-IDF", "country": "France" }
          }
        ]
        """);

    List<LocationCandidate> result = geocodingService.search("Paris", null, "France");

    assertEquals(1, result.size());
    assertEquals(48.8534951, result.get(0).lat()); // first copy kept
    mockServer.verify();
  }

  @Test
  void searchShouldDropDuplicateCoordinatesKeepingFirst() {
    respondWith("""
        [
          {
            "lat": "6.8032561", "lon": "-58.1455403",
            "addresstype": "city", "name": "City of Georgetown",
            "display_name": "City of Georgetown, Demerara-Mahaica, Guyana",
            "address": { "city": "City of Georgetown", "state": "Demerara-Mahaica",
                         "ISO3166-2-lvl4": "GY-DE", "country": "Guyana" }
          },
          {
            "lat": "6.8032561", "lon": "-58.1455403",
            "addresstype": "city", "name": "Georgetown",
            "display_name": "Georgetown, Plaisance-Industry Local Government, Demerara-Mahaica, Guyana",
            "address": { "city": "Georgetown", "state": "Demerara-Mahaica",
                         "ISO3166-2-lvl4": "GY-DE", "country": "Guyana" }
          }
        ]
        """);

    List<LocationCandidate> result = geocodingService.search("Georgetown", null, "Guyana");

    assertEquals(1, result.size());
    assertEquals("City of Georgetown", result.get(0).cityName()); // first copy kept
    mockServer.verify();
  }

  @Test
  void searchShouldKeepDistinctPlacesWithSameName() {
    respondWith("""
        [
          {
            "lat": "39.7990175", "lon": "-89.6439575",
            "addresstype": "city", "name": "Springfield",
            "display_name": "Springfield, Sangamon County, Illinois, United States",
            "address": { "state": "Illinois", "ISO3166-2-lvl4": "US-IL", "country": "United States" }
          },
          {
            "lat": "42.1018764", "lon": "-72.5886727",
            "addresstype": "city", "name": "Springfield",
            "display_name": "Springfield, Hampden County, Massachusetts, United States",
            "address": { "state": "Massachusetts", "ISO3166-2-lvl4": "US-MA", "country": "United States" }
          },
          {
            "lat": "37.2081729", "lon": "-93.2922715",
            "addresstype": "city", "name": "Springfield",
            "display_name": "Springfield, Greene County, Missouri, United States",
            "address": { "state": "Missouri", "ISO3166-2-lvl4": "US-MO", "country": "United States" }
          }
        ]
        """);

    List<LocationCandidate> result = geocodingService.search("Springfield", null, "United States");

    assertEquals(3, result.size());
    mockServer.verify();
  }

  // ---- request ----

  @Test
  void searchShouldSendJsonV2AddressDetailsEnglishAndLimit() {
    mockServer.expect(requestTo(containsString("/search")))
        .andExpect(queryParam("format", "jsonv2"))
        .andExpect(queryParam("addressdetails", "1"))
        .andExpect(queryParam("accept-language", "en"))
        .andExpect(queryParam("limit", "5"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    geocodingService.search("Paris", null, "France");

    mockServer.verify();
  }

  @Test
  void searchShouldSendStateWhenRegionGiven() {
    mockServer.expect(requestTo(containsString("/search")))
        .andExpect(queryParam("state", "Missouri"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    geocodingService.search("Springfield", "Missouri", "United States");

    mockServer.verify();
  }

  @Test
  void searchShouldOmitStateWhenRegionNull() {
    mockServer.expect(requestTo(not(containsString("state="))))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    geocodingService.search("Springfield", null, "United States");

    mockServer.verify();
  }

  // ---- empty and errors ----

  @Test
  void searchShouldReturnEmptyListWhenNoMatches() {
    respondWith("[]");

    assertTrue(geocodingService.search("Pariss", null, "France").isEmpty());
    mockServer.verify();
  }

  @Test
  void searchShouldThrowUnavailableWhenServerErrors() {
    mockServer.expect(requestTo(containsString("/search"))).andRespond(withServerError());

    assertThrows(GeocodingUnavailableException.class,
        () -> geocodingService.search("Paris", null, "France"));
    mockServer.verify();
  }

  @Test
  void searchShouldThrowUnavailableWhenRateLimited() {
    mockServer.expect(requestTo(containsString("/search")))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThrows(GeocodingUnavailableException.class,
        () -> geocodingService.search("Paris", null, "France"));
    mockServer.verify();
  }

  // ---- cache key ----

  @Test
  void cacheKeyShouldIgnoreCase() {
    assertEquals(GeocodingService.cacheKey("Springfield", "Missouri", "United States"),
        GeocodingService.cacheKey("SPRINGFIELD", "missouri", "united states"));
  }

  @Test
  void cacheKeyShouldTreatNullRegionAsEmpty() {
    assertEquals("springfield||united states", GeocodingService.cacheKey("Springfield", null, "United States"));
  }

  @Test
  void cacheKeyShouldDifferByRegion() {
    assertNotEquals(GeocodingService.cacheKey("Springfield", null, "United States"),
        GeocodingService.cacheKey("Springfield", "Missouri", "United States"));
  }
}