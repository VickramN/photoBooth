package com.example.photoBooth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.photoBooth.entity.Album;
import com.example.photoBooth.entity.Image;
import com.example.photoBooth.repository.AlbumRepository;
import com.example.photoBooth.repository.ImageRepository;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    private final ImageRepository imageRepository;
    private final AlbumRepository albumRepository;

    public ImageService(ImageRepository imageRepository, AlbumRepository albumRepository) {
        this.imageRepository = imageRepository;
        this.albumRepository = albumRepository;
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

    public Optional<Image> create(int albumId, String imgValue) {
        logger.info("Creating image for album {}", albumId);

        Optional<Album> optionalAlbum = albumRepository.findById(albumId);

        if (optionalAlbum.isEmpty()) {
            logger.warn("Cannot create image. Album not found with id {}", albumId);
            return Optional.empty();
        }

        Album album = optionalAlbum.get();

        Image image = new Image();
        image.setImg(imgValue);
        image.setAlbum(album);

        Image savedImage = imageRepository.save(image);

        logger.info("Image created successfully with id {}", savedImage.getId());

        return Optional.of(savedImage);
    }

    @Transactional
    public void deleteByAlbumIdAndImageId(int albumId, int imageId) {
        logger.info("Deleting image {} from album {}", imageId, albumId);

        imageRepository.deleteByAlbum_IdAndId(albumId, imageId);

        logger.info("Delete operation completed for image {}", imageId);
    }
}