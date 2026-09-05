package com.example.photoBooth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.photoBooth.api.CreateAlbumRequest;
import com.example.photoBooth.entity.Album;
import com.example.photoBooth.security.UserPrincipal;
import com.example.photoBooth.service.AlbumService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/albums")
public class AlbumController {

    private static final Logger logger = LoggerFactory.getLogger(AlbumController.class);

    private final AlbumService albumService;

    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }

    @GetMapping
    public List<Album> getAlbums(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID ownerId = principal.getId();

        if (city != null && country != null) {
            logger.info("GET /albums?city={}&country={} - Fetching albums by city and country", city, country);
            return albumService.findByCityNameAndCountryName(city, country, ownerId);
        }

        if (city != null) {
            logger.info("GET /albums?city={} - Fetching albums by city", city);
            return albumService.findByCityName(city, ownerId);
        }

        logger.info("GET /albums - Fetching all albums");
        return albumService.findAll(ownerId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Album> getAlbumById(@PathVariable UUID id,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        logger.info("GET /albums/{} - Fetching album by id", id);

        return albumService.findById(id, principal.getId())
                .map(album -> {
                    logger.info("Album found with id {}", id);
                    return ResponseEntity.ok(album);
                })
                .orElseGet(() -> {
                    logger.warn("Album not found with id {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PostMapping
    public ResponseEntity<Album> createAlbum(@RequestBody CreateAlbumRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        logger.info("POST /albums - Creating album with name {}", request.getAlbumName());

        Album album = new Album();
        album.setAlbumName(request.getAlbumName());
        album.setCityName(request.getCityName());
        album.setCountryName(request.getCountryName());

        Album savedAlbum = albumService.create(album, principal.getId());

        logger.info("Album created successfully with id {}", savedAlbum.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(savedAlbum);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlbum(@PathVariable UUID id,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        logger.info("DELETE /albums/{} - Attempting to delete album", id);

        boolean deleted = albumService.deleteById(id, principal.getId());

        if (!deleted) {
            logger.warn("Cannot delete album. Album not found with id {}", id);
            return ResponseEntity.notFound().build();
        }

        logger.info("Album deleted successfully with id {}", id);
        return ResponseEntity.noContent().build();
    }
}