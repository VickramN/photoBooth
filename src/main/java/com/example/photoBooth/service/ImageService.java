package com.example.photoBooth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.photoBooth.entity.Album;
import com.example.photoBooth.entity.Image;
import com.example.photoBooth.repository.AlbumRepository;
import com.example.photoBooth.repository.ImageRepository;

import jakarta.transaction.Transactional;

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

    public ImageService(ImageRepository imageRepository, AlbumRepository albumRepository,
                         ImageStorageService imageStorageService) {
        this.imageRepository = imageRepository;
        this.albumRepository = albumRepository;
        this.imageStorageService = imageStorageService;
    }

    public Optional<List<Image>> findByAlbumId(UUID albumId, UUID ownerId) {
        logger.info("Fetching images for album {} for owner {}", albumId, ownerId);

        if (!isAlbumOwnedBy(albumId, ownerId)) {
            logger.warn("Cannot fetch images. Album {} not found or not owned by {}", albumId, ownerId);
            return Optional.empty();
        }

        return Optional.of(imageRepository.findByAlbum_Id(albumId));
    }

    public Optional<Image> findById(UUID id, UUID ownerId) {
        logger.info("Searching for image with id {} for owner {}", id, ownerId);

        Optional<Image> image = imageRepository.findById(id)
                .filter(img -> img.getAlbum() != null && ownerId.equals(img.getAlbum().getOwnerId()));

        if (image.isPresent()) {
            logger.info("Image found with id {}", id);
        } else {
            logger.warn("Image not found (or not owned) with id {}", id);
        }

        return image;
    }

    public Optional<Image> create(UUID albumId, MultipartFile file, UUID ownerId) {
        logger.info("Creating image for album {} for owner {}", albumId, ownerId);

        Optional<Album> optionalAlbum = albumRepository.findById(albumId)
                .filter(album -> ownerId.equals(album.getOwnerId()));

        if (optionalAlbum.isEmpty()) {
            logger.warn("Cannot create image. Album {} not found or not owned by {}", albumId, ownerId);
            return Optional.empty();
        }

        Album album = optionalAlbum.get();

        String imageUrl;
        try {
            imageUrl = imageStorageService.upload(
                    albumId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }

        Image image = new Image();
        image.setImg(imageUrl);
        image.setAlbum(album);

        Image savedImage = imageRepository.save(image);

        logger.info("Image created successfully with id {}", savedImage.getId());

        return Optional.of(savedImage);
    }

    @Transactional
    public boolean deleteByAlbumIdAndImageId(UUID albumId, UUID imageId, UUID ownerId) {
        logger.info("Deleting image {} from album {} for owner {}", imageId, albumId, ownerId);

        if (!isAlbumOwnedBy(albumId, ownerId)) {
            logger.warn("Cannot delete. Album {} not found or not owned by {}", albumId, ownerId);
            return false;
        }

        Optional<Image> optionalImage = imageRepository.findById(imageId);

        if (optionalImage.isEmpty() || !albumId.equals(optionalImage.get().getAlbumId())) {
            logger.warn("Cannot delete. Image {} not found in album {}", imageId, albumId);
            return false;
        }

        Image image = optionalImage.get();
        imageStorageService.delete(image.getImg());

        imageRepository.deleteByAlbum_IdAndId(albumId, imageId);

        logger.info("Delete operation completed for image {}", imageId);
        return true;
    }

    private boolean isAlbumOwnedBy(UUID albumId, UUID ownerId) {
        return albumRepository.findById(albumId)
                .map(album -> ownerId.equals(album.getOwnerId()))
                .orElse(false);
    }
}