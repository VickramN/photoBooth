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
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private ImageStorageService imageStorageService;

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
    void createShouldSaveImageWhenAlbumExists() throws Exception {
        Album album = new Album();
        album.setId(1);
        album.setAlbumName("Test Album");

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        String uploadedUrl = "https://account.r2.cloudflarestorage.com/bucket/albums/1/uuid.jpg";

        Image savedImage = new Image();
        savedImage.setId(1);
        savedImage.setImg(uploadedUrl);
        savedImage.setAlbum(album);

        when(albumRepository.findById(1)).thenReturn(Optional.of(album));
        when(imageStorageService.upload(eq(1), eq("photo.jpg"), eq("image/jpeg"), any(byte[].class)))
                .thenReturn(uploadedUrl);
        when(imageRepository.save(any(Image.class))).thenReturn(savedImage);

        Optional<Image> result = imageService.create(1, file);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().getId());
        assertEquals(uploadedUrl, result.get().getImg());
        verify(albumRepository).findById(1);
        verify(imageStorageService).upload(eq(1), eq("photo.jpg"), eq("image/jpeg"), any(byte[].class));
        verify(imageRepository).save(any(Image.class));
    }

    @Test
    void createShouldReturnEmptyWhenAlbumDoesNotExist() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        when(albumRepository.findById(99)).thenReturn(Optional.empty());

        Optional<Image> result = imageService.create(99, file);

        assertTrue(result.isEmpty());
        verify(albumRepository).findById(99);
        verify(imageRepository, never()).save(any(Image.class));
        verifyNoInteractions(imageStorageService);
    }

    @Test
    void deleteByAlbumIdAndImageIdShouldDeleteImage() {
        Album album = new Album();
        album.setId(1);

        Image image = new Image();
        image.setId(2);
        image.setImg("https://account.r2.cloudflarestorage.com/bucket/albums/1/uuid.jpg");
        image.setAlbum(album);

        when(imageRepository.findById(2)).thenReturn(Optional.of(image));

        imageService.deleteByAlbumIdAndImageId(1, 2);

        verify(imageStorageService).delete("https://account.r2.cloudflarestorage.com/bucket/albums/1/uuid.jpg");
        verify(imageRepository).deleteByAlbum_IdAndId(1, 2);
    }

    @Test
    void deleteByAlbumIdAndImageIdShouldNotDeleteWhenImageBelongsToDifferentAlbum() {
        Album album = new Album();
        album.setId(5);

        Image image = new Image();
        image.setId(2);
        image.setImg("https://account.r2.cloudflarestorage.com/bucket/albums/5/uuid.jpg");
        image.setAlbum(album);

        when(imageRepository.findById(2)).thenReturn(Optional.of(image));

        imageService.deleteByAlbumIdAndImageId(1, 2);

        verifyNoInteractions(imageStorageService);
        verify(imageRepository, never()).deleteByAlbum_IdAndId(anyInt(), anyInt());
    }
}