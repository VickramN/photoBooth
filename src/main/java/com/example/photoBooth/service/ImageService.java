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

    public List<Image> findByAlbumId(int albumId) {
        logger.info("Fetching images for album {}", albumId);

        return imageRepository.findByAlbum_Id(albumId);
    }

    public Optional<Image> findById(int id) {
        logger.info("Searching for image with id {}", id);

        Optional<Image> image = imageRepository.findById(id);

        if (image.isPresent()) {
            logger.info("Image found with id {}", id);
        } else {
            logger.warn("Image not found with id {}", id);
        }

        return image;
    }

    public Optional<Image> create(int albumId, MultipartFile file) {
        logger.info("Creating image for album {}", albumId);

        Optional<Album> optionalAlbum = albumRepository.findById(albumId);

        if (optionalAlbum.isEmpty()) {
            logger.warn("Cannot create image. Album not found with id {}", albumId);
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
    public void deleteByAlbumIdAndImageId(int albumId, int imageId) {
        logger.info("Deleting image {} from album {}", imageId, albumId);

        Optional<Image> optionalImage = imageRepository.findById(imageId);

        if (optionalImage.isEmpty() || !Integer.valueOf(albumId).equals(optionalImage.get().getAlbumId())) {
            logger.warn("Cannot delete. Image {} not found in album {}", imageId, albumId);
            return;
        }

        Image image = optionalImage.get();
        imageStorageService.delete(image.getImg());

        imageRepository.deleteByAlbum_IdAndId(albumId, imageId);

        logger.info("Delete operation completed for image {}", imageId);
    }
}