package com.example.photoBooth.service;

import com.example.photoBooth.entity.Album;
import com.example.photoBooth.entity.Image;
import com.example.photoBooth.entity.User;
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

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OTHER_OWNER_ID = UUID.randomUUID();
    private static final UUID ALBUM_ID = UUID.randomUUID();
    private static final UUID MISSING_ALBUM_ID = UUID.randomUUID();
    private static final UUID IMAGE_ID = UUID.randomUUID();
    private static final UUID MISSING_IMAGE_ID = UUID.randomUUID();

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private ImageService imageService;

    private User owner(UUID ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        return owner;
    }

    private Album ownedAlbum(UUID albumId, UUID ownerId) {
        Album album = new Album();
        album.setId(albumId);
        album.setOwner(owner(ownerId));
        return album;
    }

    @Test
    void findByAlbumIdShouldReturnImagesWhenOwned() {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setImg("test-image-url");
        image.setAlbum(album);

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        when(imageRepository.findByAlbum_Id(ALBUM_ID)).thenReturn(List.of(image));

        Optional<List<Image>> result = imageService.findByAlbumId(ALBUM_ID, OWNER_ID);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
        assertEquals("test-image-url", result.get().get(0).getImg());
    }

    @Test
    void findByAlbumIdShouldReturnEmptyOptionalWhenAlbumNotFound() {
        when(albumRepository.findById(MISSING_ALBUM_ID)).thenReturn(Optional.empty());

        Optional<List<Image>> result = imageService.findByAlbumId(MISSING_ALBUM_ID, OWNER_ID);

        assertTrue(result.isEmpty());
        verify(imageRepository, never()).findByAlbum_Id(any(UUID.class));
    }

    @Test
    void findByAlbumIdShouldReturnEmptyOptionalWhenNotOwned() {
        Album album = ownedAlbum(ALBUM_ID, OTHER_OWNER_ID);

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));

        Optional<List<Image>> result = imageService.findByAlbumId(ALBUM_ID, OWNER_ID);

        assertTrue(result.isEmpty());
        verify(imageRepository, never()).findByAlbum_Id(any(UUID.class));
    }

    @Test
    void findByIdShouldReturnImageWhenOwned() {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setImg("test-image-url");
        image.setAlbum(album);

        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(image));

        Optional<Image> result = imageService.findById(IMAGE_ID, OWNER_ID);

        assertTrue(result.isPresent());
        assertEquals("test-image-url", result.get().getImg());
    }

    @Test
    void findByIdShouldReturnEmptyWhenNotOwned() {
        Album album = ownedAlbum(ALBUM_ID, OTHER_OWNER_ID);
        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setAlbum(album);

        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(image));

        Optional<Image> result = imageService.findById(IMAGE_ID, OWNER_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void createShouldSaveImageWhenAlbumOwned() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);

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

        Optional<Image> result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertTrue(result.isPresent());
        assertEquals(IMAGE_ID, result.get().getId());
        assertEquals(uploadedUrl, result.get().getImg());
    }

    @Test
    void createShouldReturnEmptyWhenAlbumNotFound() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        when(albumRepository.findById(MISSING_ALBUM_ID)).thenReturn(Optional.empty());

        Optional<Image> result = imageService.create(MISSING_ALBUM_ID, file, OWNER_ID);

        assertTrue(result.isEmpty());
        verifyNoInteractions(imageStorageService);
        verify(imageRepository, never()).save(any(Image.class));
    }

    @Test
    void createShouldReturnEmptyWhenAlbumNotOwned() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OTHER_OWNER_ID);

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));

        Optional<Image> result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertTrue(result.isEmpty());
        verifyNoInteractions(imageStorageService);
        verify(imageRepository, never()).save(any(Image.class));
    }

    @Test
    void deleteByAlbumIdAndImageIdShouldReturnTrueWhenOwned() {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setImg("https://pub-example.r2.dev/albums/" + ALBUM_ID + "/uuid.jpg");
        image.setAlbum(album);

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(image));

        boolean result = imageService.deleteByAlbumIdAndImageId(ALBUM_ID, IMAGE_ID, OWNER_ID);

        assertTrue(result);
        verify(imageStorageService).delete(image.getImg());
        verify(imageRepository).deleteByAlbum_IdAndId(ALBUM_ID, IMAGE_ID);
    }

    @Test
    void deleteByAlbumIdAndImageIdShouldReturnFalseWhenAlbumNotOwned() {
        Album album = ownedAlbum(ALBUM_ID, OTHER_OWNER_ID);

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));

        boolean result = imageService.deleteByAlbumIdAndImageId(ALBUM_ID, IMAGE_ID, OWNER_ID);

        assertFalse(result);
        verifyNoInteractions(imageStorageService);
        verify(imageRepository, never()).deleteByAlbum_IdAndId(any(UUID.class), any(UUID.class));
    }

    @Test
    void deleteByAlbumIdAndImageIdShouldReturnFalseWhenImageNotFound() {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        when(imageRepository.findById(MISSING_IMAGE_ID)).thenReturn(Optional.empty());

        boolean result = imageService.deleteByAlbumIdAndImageId(ALBUM_ID, MISSING_IMAGE_ID, OWNER_ID);

        assertFalse(result);
        verifyNoInteractions(imageStorageService);
    }
}