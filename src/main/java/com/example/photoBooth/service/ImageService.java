package com.example.photoBooth.service;

import com.example.photoBooth.api.ImageResponse;
import com.example.photoBooth.config.UploadProperties;
import com.example.photoBooth.entity.Album;
import com.example.photoBooth.entity.Image;
import com.example.photoBooth.repository.AlbumRepository;
import com.example.photoBooth.repository.ImageRepository;
import com.example.photoBooth.service.upload.ClamAvClient;
import com.example.photoBooth.service.upload.ContentTypeValidator;
import com.example.photoBooth.service.upload.ImageReencoder;
import com.example.photoBooth.service.upload.PresignedUrlService;
import com.example.photoBooth.service.upload.RateLimiterService;
import com.example.photoBooth.service.upload.UploadReadException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    private final ImageRepository imageRepository;
    private final AlbumRepository albumRepository;
    private final ImageStorageService imageStorageService;
    private final RateLimiterService rateLimiterService;
    private final ContentTypeValidator contentTypeValidator;
    private final ImageReencoder imageReencoder;
    private final ClamAvClient clamAvClient;
    private final PresignedUrlService presignedUrlService;
    private final UploadProperties uploadProperties;

    public ImageService(ImageRepository imageRepository, AlbumRepository albumRepository,
            ImageStorageService imageStorageService, RateLimiterService rateLimiterService,
            ContentTypeValidator contentTypeValidator, ImageReencoder imageReencoder,
            ClamAvClient clamAvClient, PresignedUrlService presignedUrlService,
            UploadProperties uploadProperties) {
        this.imageRepository = imageRepository;
        this.albumRepository = albumRepository;
        this.imageStorageService = imageStorageService;
        this.rateLimiterService = rateLimiterService;
        this.contentTypeValidator = contentTypeValidator;
        this.imageReencoder = imageReencoder;
        this.clamAvClient = clamAvClient;
        this.presignedUrlService = presignedUrlService;
        this.uploadProperties = uploadProperties;
    }

    public Optional<List<Image>> findByAlbumId(UUID albumId, UUID ownerId) {
        if (!isAlbumOwnedBy(albumId, ownerId)) {
            return Optional.empty();
        }
        return Optional.of(imageRepository.findByAlbum_Id(albumId));
    }

    public Optional<Image> findById(UUID id, UUID ownerId) {
        return imageRepository.findById(id)
                .filter(img -> img.getAlbum() != null && ownerId.equals(img.getAlbum().getOwnerId()));
    }

    public ImageResponse toResponse(Image image) {
        String url = presignedUrlService.generateGetUrl(image.getObjectKey());
        return new ImageResponse(image.getId(), image.getAlbumId(), url);
    }

    public ImageUploadResult create(UUID albumId, MultipartFile file, UUID ownerId) {
        Optional<Album> optionalAlbum = albumRepository.findById(albumId)
                .filter(album -> ownerId.equals(album.getOwnerId()));
        if (optionalAlbum.isEmpty()) {
            logger.warn("Cannot create image. Album {} not found or not owned by {}", albumId, ownerId);
            return new ImageUploadResult.Failure(UploadError.ALBUM_NOT_FOUND);
        }

        if (!rateLimiterService.tryConsumeUploadToken(ownerId)) {
            logger.warn("Upload rate limit exceeded for owner {}", ownerId);
            return new ImageUploadResult.Failure(UploadError.RATE_LIMITED);
        }

        byte[] originalBytes;
        try {
            originalBytes = file.getBytes();
        } catch (IOException e) {
            throw new UploadReadException("Failed to read uploaded file", e);
        }

        if (originalBytes.length > uploadProperties.getMaxFileSizeBytes()) {
            logger.warn("Upload rejected, file too large ({} bytes) for owner {}", originalBytes.length, ownerId);
            return new ImageUploadResult.Failure(UploadError.FILE_TOO_LARGE);
        }

        if (!contentTypeValidator.isAllowedImage(originalBytes)) {
            logger.warn("Upload rejected, not a genuine allowed image type for owner {}", ownerId);
            return new ImageUploadResult.Failure(UploadError.INVALID_IMAGE_TYPE);
        }

        byte[] reencodedBytes;
        try {
            reencodedBytes = imageReencoder.reencode(originalBytes, uploadProperties.getMaxDimensionPx());
        } catch (IOException e) {
            logger.warn("Upload rejected, unable to re-encode image for owner {}", ownerId);
            return new ImageUploadResult.Failure(UploadError.INVALID_IMAGE_TYPE);
        }

        if (clamAvClient.isInfected(reencodedBytes)) {
            logger.warn("Upload rejected, malware detected for owner {}", ownerId);
            return new ImageUploadResult.Failure(UploadError.INFECTED_FILE);
        }

        Album album = optionalAlbum.get();
        UUID storageId = UUID.randomUUID();
        String objectKey = imageStorageService.upload(ownerId, albumId, storageId, reencodedBytes);

        Image image = new Image();
        image.setObjectKey(objectKey);
        image.setAlbum(album);

        Image savedImage = imageRepository.save(image);
        logger.info("Image created successfully with id {}", savedImage.getId());

        return new ImageUploadResult.Success(savedImage);
    }

    @Transactional
    public boolean deleteByAlbumIdAndImageId(UUID albumId, UUID imageId, UUID ownerId) {
        if (!isAlbumOwnedBy(albumId, ownerId)) {
            return false;
        }

        Optional<Image> optionalImage = imageRepository.findById(imageId);
        if (optionalImage.isEmpty() || !albumId.equals(optionalImage.get().getAlbumId())) {
            return false;
        }

        Image image = optionalImage.get();
        imageStorageService.delete(image.getObjectKey());
        imageRepository.deleteByAlbum_IdAndId(albumId, imageId);

        return true;
    }

    private boolean isAlbumOwnedBy(UUID albumId, UUID ownerId) {
        return albumRepository.findById(albumId)
                .map(album -> ownerId.equals(album.getOwnerId()))
                .orElse(false);
    }
}
