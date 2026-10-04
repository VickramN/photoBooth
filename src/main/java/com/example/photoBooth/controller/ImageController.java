package com.example.photoBooth.controller;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.photoBooth.api.ErrorCode;
import com.example.photoBooth.api.ImageResponse;
import com.example.photoBooth.controller.error.ApiException;
import com.example.photoBooth.security.UserPrincipal;
import com.example.photoBooth.service.ImageService;
import com.example.photoBooth.service.ImageUploadResult;

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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ALBUM_NOT_FOUND));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageResponse> createImage(
            @PathVariable UUID albumId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {

        logger.info("POST /albums/{}/images - Creating image for album", albumId);

        ImageUploadResult result = imageService.create(albumId, file, principal.getId());

        if (result instanceof ImageUploadResult.Success success) {
            return ResponseEntity.status(HttpStatus.CREATED).body(imageService.toResponse(success.image()));
        }

        ImageUploadResult.Failure failure = (ImageUploadResult.Failure) result;
        throw switch (failure.error()) {
            case ALBUM_NOT_FOUND -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ALBUM_NOT_FOUND);
            case RATE_LIMITED -> new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED);
            case FILE_TOO_LARGE -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.FILE_TOO_LARGE);
            case INVALID_IMAGE_TYPE -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_IMAGE_TYPE);
            case INFECTED_FILE -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.INFECTED_FILE);
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
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.IMAGE_NOT_FOUND);
        }
        return ResponseEntity.noContent().build();
    }
}
