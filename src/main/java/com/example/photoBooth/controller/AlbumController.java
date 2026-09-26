package com.example.photoBooth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.photoBooth.api.AlbumResponse;
import com.example.photoBooth.api.CreateAlbumRequest;
import com.example.photoBooth.entity.Album;
import com.example.photoBooth.security.UserPrincipal;
import com.example.photoBooth.service.AlbumService;
import com.example.photoBooth.service.ImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/albums")
public class AlbumController {

    private static final Logger logger = LoggerFactory.getLogger(AlbumController.class);

    private final AlbumService albumService;
    private final ImageService imageService;

    public AlbumController(AlbumService albumService, ImageService imageService) {
        this.albumService = albumService;
        this.imageService = imageService;
    }

    private AlbumResponse toResponse(Album album) {
        return new AlbumResponse(
                album.getId(),
                album.getAlbumName(),
                album.getCityName(),
                album.getCountryName(),
                album.getLat(),
                album.getLang(),
                album.getImages().stream().map(imageService::toResponse).toList());
    }

    @GetMapping
    public List<AlbumResponse> getAlbums(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID ownerId = principal.getId();

        if (city != null && country != null) {
            logger.info("GET /albums?city={}&country={} - Fetching albums by city and country", city, country);
            return albumService.findByCityNameAndCountryName(city, country, ownerId).stream().map(this::toResponse).toList();
        }

        if (city != null) {
            logger.info("GET /albums?city={} - Fetching albums by city", city);
            return albumService.findByCityName(city, ownerId).stream().map(this::toResponse).toList();
        }

        logger.info("GET /albums - Fetching all albums");
        return albumService.findAll(ownerId).stream().map(this::toResponse).toList();
    }


    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AlbumResponse> getAllAlbumsAdmin(){
        logger.info("GET /albums/admin/all - Fetching all albums (admin only)");
        return albumService.findAllAdmin().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlbumResponse> getAlbumById(@PathVariable UUID id,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        logger.info("GET /albums/{} - Fetching album by id", id);

        return albumService.findById(id, principal.getId())
                .map(album -> {
                    logger.info("Album found with id {}", id);
                    return ResponseEntity.ok(toResponse(album));
                })
                .orElseGet(() -> {
                    logger.warn("Album not found with id {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PostMapping
    public ResponseEntity<AlbumResponse> createAlbum(@RequestBody CreateAlbumRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        logger.info("POST /albums - Creating album with name {}", request.getAlbumName());

        Album album = new Album();
        album.setAlbumName(request.getAlbumName());
        album.setCityName(request.getCityName());
        album.setCountryName(request.getCountryName());

        Album savedAlbum = albumService.create(album, principal.getId());

        logger.info("Album created successfully with id {}", savedAlbum.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(savedAlbum));
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