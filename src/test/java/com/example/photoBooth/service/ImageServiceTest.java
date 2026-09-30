package com.example.photoBooth.service;

import com.example.photoBooth.api.ImageResponse;
import com.example.photoBooth.entity.Album;
import com.example.photoBooth.entity.Image;
import com.example.photoBooth.entity.User;
import com.example.photoBooth.config.UploadProperties;
import com.example.photoBooth.repository.AlbumRepository;
import com.example.photoBooth.repository.ImageRepository;
import com.example.photoBooth.service.upload.ClamAvClient;
import com.example.photoBooth.service.upload.ClamAvUnavailableException;
import com.example.photoBooth.service.upload.ContentTypeValidator;
import com.example.photoBooth.service.upload.ImageReencoder;
import com.example.photoBooth.service.upload.PresignedUrlService;
import com.example.photoBooth.service.upload.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private ContentTypeValidator contentTypeValidator;
    @Mock
    private ImageReencoder imageReencoder;
    @Mock
    private ClamAvClient clamAvClient;
    @Mock
    private PresignedUrlService presignedUrlService;

    private ImageService imageService;

    @BeforeEach
    void setUp() {
        UploadProperties uploadProperties = new UploadProperties();
        imageService = new ImageService(imageRepository, albumRepository, imageStorageService,
                rateLimiterService, contentTypeValidator, imageReencoder, clamAvClient,
                presignedUrlService, uploadProperties);
    }

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

    private void stubHappyPathUpToContentType(byte[] originalBytes) {
        when(rateLimiterService.tryConsumeUploadToken(OWNER_ID)).thenReturn(true);
        when(contentTypeValidator.isAllowedImage(originalBytes)).thenReturn(true);
    }

    @Test
    void createShouldSaveImageWhenAllChecksPass() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        byte[] originalBytes = "original-bytes".getBytes();
        byte[] reencodedBytes = "reencoded-bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", originalBytes);

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        stubHappyPathUpToContentType(originalBytes);
        when(clamAvClient.isInfected(originalBytes)).thenReturn(false);
        when(imageReencoder.reencode(eq(originalBytes), anyInt())).thenReturn(reencodedBytes);
        when(imageStorageService.upload(eq(OWNER_ID), eq(ALBUM_ID), any(UUID.class), eq(reencodedBytes)))
                .thenReturn("users/" + OWNER_ID + "/albums/" + ALBUM_ID + "/storage.jpg");
        Image savedImage = new Image();
        savedImage.setId(IMAGE_ID);
        savedImage.setObjectKey("users/" + OWNER_ID + "/albums/" + ALBUM_ID + "/storage.jpg");
        savedImage.setAlbum(album);
        when(imageRepository.save(any(Image.class))).thenReturn(savedImage);

        ImageUploadResult result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertInstanceOf(ImageUploadResult.Success.class, result);
        assertEquals(IMAGE_ID, ((ImageUploadResult.Success) result).image().getId());
    }

    @Test
    void createShouldFailWithAlbumNotFoundWhenAlbumMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes());
        when(albumRepository.findById(MISSING_ALBUM_ID)).thenReturn(Optional.empty());

        ImageUploadResult result = imageService.create(MISSING_ALBUM_ID, file, OWNER_ID);

        assertEquals(new ImageUploadResult.Failure(UploadError.ALBUM_NOT_FOUND), result);
        verifyNoInteractions(rateLimiterService, contentTypeValidator, imageReencoder, clamAvClient, imageStorageService);
    }

    @Test
    void createShouldFailWithAlbumNotFoundWhenNotOwned() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OTHER_OWNER_ID);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes());
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));

        ImageUploadResult result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertEquals(new ImageUploadResult.Failure(UploadError.ALBUM_NOT_FOUND), result);
    }

    @Test
    void createShouldFailWithRateLimitedWhenLimiterDenies() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes());
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        when(rateLimiterService.tryConsumeUploadToken(OWNER_ID)).thenReturn(false);

        ImageUploadResult result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertEquals(new ImageUploadResult.Failure(UploadError.RATE_LIMITED), result);
        verifyNoInteractions(contentTypeValidator, imageReencoder, clamAvClient, imageStorageService);
    }

    @Test
    void createShouldFailWithFileTooLargeWhenOverLimit() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        byte[] oversized = new byte[11];
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", oversized);
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        when(rateLimiterService.tryConsumeUploadToken(OWNER_ID)).thenReturn(true);

        UploadProperties tinyLimitProperties = new UploadProperties();
        tinyLimitProperties.setMaxFileSizeBytes(10L);
        imageService = new ImageService(imageRepository, albumRepository, imageStorageService,
                rateLimiterService, contentTypeValidator, imageReencoder, clamAvClient,
                presignedUrlService, tinyLimitProperties);

        ImageUploadResult result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertEquals(new ImageUploadResult.Failure(UploadError.FILE_TOO_LARGE), result);
        verifyNoInteractions(contentTypeValidator, imageReencoder, clamAvClient, imageStorageService);
    }

    @Test
    void createShouldFailWithInvalidImageTypeWhenNotAGenuineImage() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        byte[] originalBytes = "not-an-image".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", originalBytes);
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        when(rateLimiterService.tryConsumeUploadToken(OWNER_ID)).thenReturn(true);
        when(contentTypeValidator.isAllowedImage(originalBytes)).thenReturn(false);

        ImageUploadResult result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertEquals(new ImageUploadResult.Failure(UploadError.INVALID_IMAGE_TYPE), result);
        verifyNoInteractions(imageReencoder, clamAvClient, imageStorageService);
    }

    @Test
    void createShouldFailWithInfectedFileWhenClamAvFlagsIt() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        byte[] originalBytes = "original-bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", originalBytes);
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        stubHappyPathUpToContentType(originalBytes);
        when(clamAvClient.isInfected(originalBytes)).thenReturn(true);

        ImageUploadResult result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertEquals(new ImageUploadResult.Failure(UploadError.INFECTED_FILE), result);
        // The scan now runs on originalBytes before re-encoding, so a flagged
        // upload should never reach the re-encoder or storage.
        verifyNoInteractions(imageReencoder, imageStorageService);
    }

    @Test
    void createShouldRefundRateLimitTokenAndRethrowWhenClamAvUnavailable() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        byte[] originalBytes = "original-bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", originalBytes);
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        stubHappyPathUpToContentType(originalBytes);
        when(clamAvClient.isInfected(originalBytes)).thenThrow(new ClamAvUnavailableException("clamd unreachable"));

        assertThrows(ClamAvUnavailableException.class, () -> imageService.create(ALBUM_ID, file, OWNER_ID));

        // A scanner outage isn't the user's fault -- the token this attempt
        // consumed should be handed back, not just discarded.
        verify(rateLimiterService).refundUploadToken(OWNER_ID);
        verifyNoInteractions(imageReencoder, imageStorageService);
    }

    @Test
    void findByAlbumIdShouldReturnImagesWhenOwned() {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setObjectKey("key");
        image.setAlbum(album);

        when(albumRepository.existsByIdAndOwner_Id(ALBUM_ID, OWNER_ID)).thenReturn(true);
        when(imageRepository.findByAlbum_IdOrderByCreatedAtAsc(ALBUM_ID)).thenReturn(List.of(image));

        Optional<List<Image>> result = imageService.findByAlbumId(ALBUM_ID, OWNER_ID);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
    }

    @Test
    void findByAlbumIdShouldReturnEmptyOptionalWhenAlbumNotFound() {
        when(albumRepository.existsByIdAndOwner_Id(MISSING_ALBUM_ID, OWNER_ID)).thenReturn(false);

        Optional<List<Image>> result = imageService.findByAlbumId(MISSING_ALBUM_ID, OWNER_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void toResponseShouldIncludePresignedUrl() {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setObjectKey("users/x/albums/y/z.jpg");
        image.setAlbum(album);
        when(presignedUrlService.generateGetUrl("users/x/albums/y/z.jpg"))
                .thenReturn("https://presigned.example/z.jpg");

        ImageResponse response = imageService.toResponse(image);

        assertEquals(IMAGE_ID, response.id());
        assertEquals(ALBUM_ID, response.albumId());
        assertEquals("https://presigned.example/z.jpg", response.url());
    }

    @Test
    void deleteByAlbumIdAndImageIdShouldReturnTrueWhenOwned() {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setObjectKey("users/x/albums/y/z.jpg");
        image.setAlbum(album);

        when(albumRepository.existsByIdAndOwner_Id(ALBUM_ID, OWNER_ID)).thenReturn(true);
        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(image));

        boolean result = imageService.deleteByAlbumIdAndImageId(ALBUM_ID, IMAGE_ID, OWNER_ID);

        assertTrue(result);
        verify(imageStorageService).delete("users/x/albums/y/z.jpg");
        verify(imageRepository).deleteByAlbum_IdAndId(ALBUM_ID, IMAGE_ID);
    }

    @Test
    void deleteByAlbumIdAndImageIdShouldReturnFalseWhenAlbumNotOwned() {
        when(albumRepository.existsByIdAndOwner_Id(ALBUM_ID, OWNER_ID)).thenReturn(false);

        boolean result = imageService.deleteByAlbumIdAndImageId(ALBUM_ID, IMAGE_ID, OWNER_ID);

        assertFalse(result);
        verifyNoInteractions(imageStorageService);
    }
}
