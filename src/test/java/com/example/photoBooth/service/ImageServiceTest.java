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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    private static final UUID ALBUM_ID = UUID.randomUUID();
    private static final UUID OTHER_ALBUM_ID = UUID.randomUUID();
    private static final UUID IMAGE_ID = UUID.randomUUID();
    private static final UUID MISSING_ALBUM_ID = UUID.randomUUID();
    private static final UUID MISSING_IMAGE_ID = UUID.randomUUID();

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
        image.setId(IMAGE_ID);
        image.setImg("test-image-url");

        when(imageRepository.findByAlbum_Id(ALBUM_ID)).thenReturn(List.of(image));

        List<Image> result = imageService.findByAlbumId(ALBUM_ID);

        assertEquals(1, result.size());
        assertEquals("test-image-url", result.get(0).getImg());
        verify(imageRepository).findByAlbum_Id(ALBUM_ID);
    }

    @Test
    void findByIdShouldReturnImageWhenFound() {
        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setImg("test-image-url");

        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(image));

        Optional<Image> result = imageService.findById(IMAGE_ID);

        assertTrue(result.isPresent());
        assertEquals("test-image-url", result.get().getImg());
        verify(imageRepository).findById(IMAGE_ID);
    }

    @Test
    void findByIdShouldReturnEmptyWhenNotFound() {
        when(imageRepository.findById(MISSING_IMAGE_ID)).thenReturn(Optional.empty());

        Optional<Image> result = imageService.findById(MISSING_IMAGE_ID);

        assertTrue(result.isEmpty());
        verify(imageRepository).findById(MISSING_IMAGE_ID);
    }

    @Test
    void createShouldSaveImageWhenAlbumExists() throws Exception {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setAlbumName("Test Album");

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        String uploadedUrl = "https://pub-example.r2.dev/albums/" + ALBUM_ID + "/uuid.jpg";

        Image savedImage = new Image();
        savedImage.setId(IMAGE_ID);
        savedImage.setImg(uploadedUrl);
        savedImage.setAlbum(album);

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        when(imageStorageService.upload(eq(ALBUM_ID), eq("photo.jpg"), eq("image/jpeg"), any(byte[].class)))
                .thenReturn(uploadedUrl);
        when(imageRepository.save(any(Image.class))).thenReturn(savedImage);

        Optional<Image> result = imageService.create(ALBUM_ID, file);

        assertTrue(result.isPresent());
        assertEquals(IMAGE_ID, result.get().getId());
        assertEquals(uploadedUrl, result.get().getImg());
        verify(albumRepository).findById(ALBUM_ID);
        verify(imageStorageService).upload(eq(ALBUM_ID), eq("photo.jpg"), eq("image/jpeg"), any(byte[].class));
        verify(imageRepository).save(any(Image.class));
    }

    @Test
    void createShouldReturnEmptyWhenAlbumDoesNotExist() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        when(albumRepository.findById(MISSING_ALBUM_ID)).thenReturn(Optional.empty());

        Optional<Image> result = imageService.create(MISSING_ALBUM_ID, file);

        assertTrue(result.isEmpty());
        verify(albumRepository).findById(MISSING_ALBUM_ID);
        verify(imageRepository, never()).save(any(Image.class));
        verifyNoInteractions(imageStorageService);
    }

    @Test
    void deleteByAlbumIdAndImageIdShouldDeleteImage() {
        Album album = new Album();
        album.setId(ALBUM_ID);

        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setImg("https://pub-example.r2.dev/albums/" + ALBUM_ID + "/uuid.jpg");
        image.setAlbum(album);

        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(image));

        imageService.deleteByAlbumIdAndImageId(ALBUM_ID, IMAGE_ID);

        verify(imageStorageService).delete("https://pub-example.r2.dev/albums/" + ALBUM_ID + "/uuid.jpg");
        verify(imageRepository).deleteByAlbum_IdAndId(ALBUM_ID, IMAGE_ID);
    }

    @Test
    void deleteByAlbumIdAndImageIdShouldNotDeleteWhenImageBelongsToDifferentAlbum() {
        Album album = new Album();
        album.setId(OTHER_ALBUM_ID);

        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setImg("https://pub-example.r2.dev/albums/" + OTHER_ALBUM_ID + "/uuid.jpg");
        image.setAlbum(album);

        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(image));

        imageService.deleteByAlbumIdAndImageId(ALBUM_ID, IMAGE_ID);

        verifyNoInteractions(imageStorageService);
        verify(imageRepository, never()).deleteByAlbum_IdAndId(any(UUID.class), any(UUID.class));
    }
}