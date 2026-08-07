package com.example.photoBooth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.photoBooth.api.CreateImageRequest;
import com.example.photoBooth.entity.Image;
import com.example.photoBooth.service.ImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/albums/{albumId}/images")
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping
    public List<Image> getImagesByAlbumId(@PathVariable int albumId) {
        logger.info("GET /albums/{}/images - Fetching images for album", albumId);
        return imageService.findByAlbumId(albumId);
    }

    @PostMapping
    public ResponseEntity<Image> createImage(
            @PathVariable int albumId,
            @RequestBody CreateImageRequest request) {

        logger.info("POST /albums/{}/images - Creating image for album", albumId);

        return imageService.create(albumId, request.getImg())
                .map(image -> {
                    logger.info("Image created successfully with id {} for album {}", image.getId(), albumId);
                    return ResponseEntity.status(HttpStatus.CREATED).body(image);
                })
                .orElseGet(() -> {
                    logger.warn("Cannot create image. Album not found with id {}", albumId);
                    return ResponseEntity.notFound().build();
                });
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable int albumId,
            @PathVariable int imageId) {

        logger.info("DELETE /albums/{}/images/{} - Deleting image", albumId, imageId);

        imageService.deleteByAlbumIdAndImageId(albumId, imageId);

        logger.info("Delete request completed for image {} in album {}", imageId, albumId);

        return ResponseEntity.noContent().build();
    }
}