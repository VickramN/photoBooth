package com.example.photoBooth.controller;

import com.example.photoBooth.api.ErrorResponse;
import com.example.photoBooth.api.ImageResponse;
import com.example.photoBooth.security.UserPrincipal;
import com.example.photoBooth.service.ImageService;
import com.example.photoBooth.service.ImageUploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/albums/{albumId}/images")
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping
    public ResponseEntity<List<ImageResponse>> getImagesByAlbumId(@PathVariable UUID albumId,
            @AuthenticationPrincipal UserPrincipal principal) {
        logger.info("GET /albums/{}/images - Fetching images for album", albumId);

        return imageService.findByAlbumId(albumId, principal.getId())
                .map(images -> images.stream().map(imageService::toResponse).toList())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> createImage(
            @PathVariable UUID albumId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {

        logger.info("POST /albums/{}/images - Creating image for album", albumId);

        ImageUploadResult result = imageService.create(albumId, file, principal.getId());

        if (result instanceof ImageUploadResult.Success success) {
            return ResponseEntity.status(HttpStatus.CREATED).body(imageService.toResponse(success.image()));
        }

        ImageUploadResult.Failure failure = (ImageUploadResult.Failure) result;
        return switch (failure.error()) {
            case ALBUM_NOT_FOUND -> ResponseEntity.notFound().build();
            case RATE_LIMITED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse("RATE_LIMITED"));
            case FILE_TOO_LARGE -> ResponseEntity.badRequest().body(new ErrorResponse("FILE_TOO_LARGE"));
            case INVALID_IMAGE_TYPE -> ResponseEntity.badRequest().body(new ErrorResponse("INVALID_IMAGE_TYPE"));
            case INFECTED_FILE -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new ErrorResponse("INFECTED_FILE"));
        };
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable UUID albumId,
            @PathVariable UUID imageId,
            @AuthenticationPrincipal UserPrincipal principal) {

        logger.info("DELETE /albums/{}/images/{} - Deleting image", albumId, imageId);

        boolean deleted = imageService.deleteByAlbumIdAndImageId(albumId, imageId, principal.getId());

        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
