package com.example.photoBooth.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.photoBooth.api.LocationCandidate;
import com.example.photoBooth.config.SecurityConfig;
import com.example.photoBooth.repository.UserRepository;
import com.example.photoBooth.security.JwtService;
import com.example.photoBooth.service.GeocodingService;
import com.example.photoBooth.service.GeocodingUnavailableException;
import com.example.photoBooth.entity.User;
import com.example.photoBooth.security.UserPrincipal;

import java.util.List;
import java.util.UUID;

@WebMvcTest(LocationController.class)
@Import(SecurityConfig.class)
public class LocationControllerTest {

    private final static UUID OWNER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeocodingService geocodingService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private UserPrincipal principal() {
        User owner = new User();
        owner.setId(OWNER_ID);
        owner.setUsername("owner");
        owner.setPassword("hashed");
        return new UserPrincipal(owner);
    }

    @Test
    void searchShouldReturnCandidates() throws Exception {

        when(geocodingService.search("Paris", "France"))
                .thenReturn(List.of(new LocationCandidate("Paris, Île-de-France, France métropolitaine, France",
                        "Paris", "France", 48.8588897, 2.3200410)));

        mockMvc.perform(get("/locations/search")
                .with(user(principal()))
                .param("city", " Paris ")
                .param("country", "France"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cityName").value("Paris"))
                .andExpect(jsonPath("$[0].countryName").value("France"))
                .andExpect(jsonPath("$.[0]lat").value(48.8588897));
    }

    @Test
    void searchShouldReturnEmptyListWhenNoMatches() throws Exception {
        when(geocodingService.search("Sabaody", "Red Line"))
                .thenReturn(List.of());

        mockMvc.perform(get("/locations/search")
                .param("city", "Sabaody")
                .param("country", "Red Line")
                .with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchShouldReturnMissingParameterWhenCityAbsent() throws Exception {

        mockMvc.perform(get("/locations/search")
                .param("country", "Konoha")
                .with(user(principal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_PARAMETER"));

        verifyNoInteractions(geocodingService);
    }

    @Test
    void searchShouldReturnInvalidParameterWhenCityBlank() throws Exception {

        mockMvc.perform(get("/locations/search")
                .param("city", "  ")
                .param("country", "Hueco Mundo")
                .with(user(principal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));

        verifyNoInteractions(geocodingService);
    }

    @Test
    void searchShouldReturnInvalidParameterWhenValueIsOverLimit() throws Exception {

        mockMvc.perform(get("/locations/search")
                .param("city",
                        "v".repeat(101))
                .param("country", "Tokyo")
                .with(user(principal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));

        verifyNoInteractions(geocodingService);
    }

    @Test
    void searchShouldReturnServiceUnavailableWhenNominatimDown() throws Exception {
        when(geocodingService.search("Paris", "France"))
                .thenThrow(new GeocodingUnavailableException("down", new RuntimeException()));

        mockMvc.perform(get("/locations/search")
                .param("city", "Paris")
                .param("country", "France")
                .with(user(principal())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("GEOCODING_UNAVAILABLE"));
    }

    @Test
    void searchShouldReturnForbiddenWhenUnauthenticated() throws Exception {
        // layer 4: change to 401 and assert the error bodyonce the entry is point is
        // configured
        mockMvc.perform(get("/locations/search")
                .param("city", "Paris")
                .param("country", "France"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(geocodingService);
    }

}
