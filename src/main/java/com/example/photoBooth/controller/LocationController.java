package com.example.photoBooth.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.photoBooth.api.ErrorCode;
import com.example.photoBooth.api.LocationCandidate;
import com.example.photoBooth.controller.error.ApiException;
import com.example.photoBooth.service.GeocodingService;

@RestController
@RequestMapping("/locations")
public class LocationController {

    private static final Logger logger = LoggerFactory.getLogger(LocationController.class);

    private static final int MAX_PARAM_LENGTH = 100;

    private final GeocodingService geocodingService;

    public LocationController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @GetMapping("/search")
    public List<LocationCandidate> search(@RequestParam String city, @RequestParam String country) {

        requireValidParam(city);
        requireValidParam(country);
        logger.info("GET /locations/search - Validate Locations");
        return geocodingService.search(city.trim(), country.trim());

    }

    private void requireValidParam(String value) {
        if (value.isBlank() || value.trim().length() > MAX_PARAM_LENGTH) {
            logger.warn("Invalid location search parameter (length {})", value.length());
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PARAMETER);
        }
    }

}
