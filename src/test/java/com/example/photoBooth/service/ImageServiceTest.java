package com.example.photoBooth.service;

import com.example.photoBooth.entity.Album;
import com.example.photoBooth.entity.Image;
import com.example.photoBooth.repository.AlbumRepository;
import com.example.photoBooth.repository.ImageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private AlbumRepository albumRepository;

    @InjectMocks
    private ImageService imageService;

    @Test
    void findByAlbumIdShouldReturnImagesForAlbum() {
        Image image = new Image();
        image.setId(1);
        image.setImg("test-image-url");

        when(imageRepository.findByAlbum_Id(1)).thenReturn(List.of(image));

        List<Image> result = imageService.findByAlbumId(1);

        assertEquals(1, result.size());
        assertEquals("test-image-url", result.get(0).getImg());
        verify(imageRepository).findByAlbum_Id(1);
    }

    @Test
    void findByIdShouldReturnImageWhenFound() {
        Image image = new Image();
        image.setId(1);
        image.setImg("test-image-url");

        when(imageRepository.findById(1)).thenReturn(Optional.of(image));

        Optional<Image> result = imageService.findById(1);

        assertTrue(result.isPresent());
        assertEquals("test-image-url", result.get().getImg());
        verify(imageRepository).findById(1);
    }

    @Test
    void findByIdShouldReturnEmptyWhenNotFound() {
        when(imageRepository.findById(99)).thenReturn(Optional.empty());

        Optional<Image> result = imageService.findById(99);

        assertTrue(result.isEmpty());
        verify(imageRepository).findById(99);
    }

    @Test
    void createShouldSaveImageWhenAlbumExists() {
        Album album = new Album();
        album.setId(1);
        album.setAlbumName("Test Album");

        Image savedImage = new Image();
        savedImage.setId(1);
        savedImage.setImg("test-image-url");
        savedImage.setAlbum(album);

        when(albumRepository.findById(1)).thenReturn(Optional.of(album));
        when(imageRepository.save(any(Image.class))).thenReturn(savedImage);

        Optional<Image> result = imageService.create(1, "test-image-url");

        assertTrue(result.isPresent());
        assertEquals(1, result.get().getId());
        assertEquals("test-image-url", result.get().getImg());
        verify(albumRepository).findById(1);
        verify(imageRepository).save(any(Image.class));
    }

    @Test
    void createShouldReturnEmptyWhenAlbumDoesNotExist() {
        when(albumRepository.findById(99)).thenReturn(Optional.empty());

        Optional<Image> result = imageService.create(99, "test-image-url");

        assertTrue(result.isEmpty());
        verify(albumRepository).findById(99);
        verify(imageRepository, never()).save(any(Image.class));
    }

    @Test
    void deleteByAlbumIdAndImageIdShouldDeleteImage() {
        imageService.deleteByAlbumIdAndImageId(1, 2);

        verify(imageRepository).deleteByAlbum_IdAndId(1, 2);
    }
}