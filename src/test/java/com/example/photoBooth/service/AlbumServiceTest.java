package com.example.photoBooth.service;

import com.example.photoBooth.entity.Album;
import com.example.photoBooth.entity.User;
import com.example.photoBooth.repository.AlbumRepository;
import com.example.photoBooth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlbumServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OTHER_OWNER_ID = UUID.randomUUID();
    private static final UUID ALBUM_ID = UUID.randomUUID();
    private static final UUID ALBUM_ID_2 = UUID.randomUUID();
    private static final UUID MISSING_ALBUM_ID = UUID.randomUUID();

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GeocodingService geocodingService;

    @InjectMocks
    private AlbumService albumService;

    private User owner() {
        User owner = new User();
        owner.setId(OWNER_ID);
        return owner;
    }

    @Test
    void findAllShouldReturnAllAlbumsForOwner() {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setOwner(owner());
        album.setAlbumName("Test Album");
        album.setCityName("Oswego");
        album.setCountryName("USA");

        when(albumRepository.findByOwner_Id(OWNER_ID)).thenReturn(List.of(album));

        List<Album> result = albumService.findAll(OWNER_ID);

        assertEquals(1, result.size());
        assertEquals("Test Album", result.get(0).getAlbumName());
        verify(albumRepository).findByOwner_Id(OWNER_ID);
    }

    @Test
    void findByIdShouldReturnAlbumWhenFoundAndOwned() {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setOwner(owner());
        album.setAlbumName("Test Album");

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));

        Optional<Album> result = albumService.findById(ALBUM_ID, OWNER_ID);

        assertTrue(result.isPresent());
        assertEquals("Test Album", result.get().getAlbumName());
    }

    @Test
    void findByIdShouldReturnEmptyWhenNotFound() {
        when(albumRepository.findById(MISSING_ALBUM_ID)).thenReturn(Optional.empty());

        Optional<Album> result = albumService.findById(MISSING_ALBUM_ID, OWNER_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByIdShouldReturnEmptyWhenFoundButNotOwned() {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setOwner(owner());
        album.setAlbumName("Someone Else's Album");

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));

        Optional<Album> result = albumService.findById(ALBUM_ID, OTHER_OWNER_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void createShouldAssignOwnerSaveAndReturnAlbum() {
        Album album = new Album();
        album.setAlbumName("Test Album");
        album.setCityName("Oswego");
        album.setCountryName("USA");

        Album savedAlbum = new Album();
        savedAlbum.setId(ALBUM_ID);
        savedAlbum.setAlbumName("Test Album");
        savedAlbum.setCityName("Oswego");
        savedAlbum.setCountryName("USA");
        savedAlbum.setLat(43.4553);
        savedAlbum.setLang(-76.5105);

        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
        when(geocodingService.geocode("Oswego", "USA"))
                .thenReturn(Optional.of(new GeocodingService.Coordinates(43.4553, -76.5105)));
        when(albumRepository.save(any(Album.class))).thenReturn(savedAlbum);

        Album result = albumService.create(album, OWNER_ID);

        assertEquals(ALBUM_ID, result.getId());
        assertEquals(43.4553, result.getLat());

        ArgumentCaptor<Album> captor = ArgumentCaptor.forClass(Album.class);
        verify(albumRepository).save(captor.capture());
        assertEquals(OWNER_ID, captor.getValue().getOwnerId());
    }

    @Test
    void createShouldThrowWhenOwnerNotFound() {
        Album album = new Album();
        album.setAlbumName("Test Album");
        album.setCityName("Oswego");
        album.setCountryName("USA");

        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> albumService.create(album, OWNER_ID));

        verify(albumRepository, never()).save(any(Album.class));
    }

    @Test
    void createShouldSaveAlbumWithNullCoordinatesWhenGeocodingFails() {
        Album album = new Album();
        album.setAlbumName("Test Album");
        album.setCityName("Nowhere");
        album.setCountryName("Nowhereland");

        Album savedAlbum = new Album();
        savedAlbum.setId(ALBUM_ID_2);
        savedAlbum.setAlbumName("Test Album");

        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
        when(geocodingService.geocode("Nowhere", "Nowhereland")).thenReturn(Optional.empty());
        when(albumRepository.save(any(Album.class))).thenReturn(savedAlbum);

        Album result = albumService.create(album, OWNER_ID);

        assertEquals(ALBUM_ID_2, result.getId());
        assertNull(result.getLat());
        assertNull(result.getLang());
    }

    @Test
    void deleteByIdShouldReturnTrueAndDeleteWhenOwned() {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setOwner(owner());

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));

        boolean result = albumService.deleteById(ALBUM_ID, OWNER_ID);

        assertTrue(result);
        verify(albumRepository).deleteById(ALBUM_ID);
    }

    @Test
    void deleteByIdShouldReturnFalseWhenNotOwned() {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setOwner(owner());

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));

        boolean result = albumService.deleteById(ALBUM_ID, OTHER_OWNER_ID);

        assertFalse(result);
        verify(albumRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void deleteByIdShouldReturnFalseWhenNotFound() {
        when(albumRepository.findById(MISSING_ALBUM_ID)).thenReturn(Optional.empty());

        boolean result = albumService.deleteById(MISSING_ALBUM_ID, OWNER_ID);

        assertFalse(result);
        verify(albumRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void findByCityNameShouldReturnMatchingAlbumsForOwner() {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setOwner(owner());
        album.setCityName("Oswego");
        album.setCountryName("USA");

        when(albumRepository.findByOwner_IdAndCityName(OWNER_ID, "Oswego")).thenReturn(List.of(album));

        List<Album> result = albumService.findByCityName("Oswego", OWNER_ID);

        assertEquals(1, result.size());
        assertEquals("Oswego", result.get(0).getCityName());
    }

    @Test
    void findByCityNameAndCountryNameShouldReturnMatchingAlbumsForOwner() {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setOwner(owner());
        album.setCityName("Oswego");
        album.setCountryName("USA");

        when(albumRepository.findByOwner_IdAndCityNameAndCountryName(OWNER_ID, "Oswego", "USA"))
                .thenReturn(List.of(album));

        List<Album> result = albumService.findByCityNameAndCountryName("Oswego", "USA", OWNER_ID);

        assertEquals(1, result.size());
        assertEquals("Oswego", result.get(0).getCityName());
        assertEquals("USA", result.get(0).getCountryName());
    }
}