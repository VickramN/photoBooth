package com.example.photoBooth.service;

import com.example.photoBooth.entity.Album;
import com.example.photoBooth.repository.AlbumRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private static final UUID ALBUM_ID = UUID.randomUUID();
    private static final UUID ALBUM_ID_2 = UUID.randomUUID();
    private static final UUID MISSING_ID = UUID.randomUUID();

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private GeocodingService geocodingService;

    @InjectMocks
    private AlbumService albumService;

    @Test
    void findAllShouldReturnAllAlbums() {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setAlbumName("Test Album");
        album.setCityName("Oswego");
        album.setCountryName("USA");

        when(albumRepository.findAll()).thenReturn(List.of(album));

        List<Album> result = albumService.findAll();

        assertEquals(1, result.size());
        assertEquals("Test Album", result.get(0).getAlbumName());
        verify(albumRepository).findAll();
    }

    @Test
    void findByIdShouldReturnAlbumWhenFound() {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setAlbumName("Test Album");

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));

        Optional<Album> result = albumService.findById(ALBUM_ID);

        assertTrue(result.isPresent());
        assertEquals("Test Album", result.get().getAlbumName());
        verify(albumRepository).findById(ALBUM_ID);
    }

    @Test
    void findByIdShouldReturnEmptyWhenNotFound() {
        when(albumRepository.findById(MISSING_ID)).thenReturn(Optional.empty());

        Optional<Album> result = albumService.findById(MISSING_ID);

        assertTrue(result.isEmpty());
        verify(albumRepository).findById(MISSING_ID);
    }

    @Test
    void createShouldSaveAndReturnAlbum() {
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

        when(geocodingService.geocode("Oswego", "USA"))
                .thenReturn(Optional.of(new GeocodingService.Coordinates(43.4553, -76.5105)));
        when(albumRepository.save(album)).thenReturn(savedAlbum);

        Album result = albumService.create(album);

        assertEquals(ALBUM_ID, result.getId());
        assertEquals("Test Album", result.getAlbumName());
        assertEquals(43.4553, result.getLat());
        assertEquals(-76.5105, result.getLang());
        verify(geocodingService).geocode("Oswego", "USA");
        verify(albumRepository).save(album);
    }

    @Test
    void deleteByIdShouldDeleteAlbum() {
        albumService.deleteById(ALBUM_ID);

        verify(albumRepository).deleteById(ALBUM_ID);
    }

    @Test
    void findByCityNameShouldReturnMatchingAlbums() {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setAlbumName("Test Album");
        album.setCityName("Oswego");
        album.setCountryName("USA");

        when(albumRepository.findByCityName("Oswego")).thenReturn(List.of(album));

        List<Album> result = albumService.findByCityName("Oswego");

        assertEquals(1, result.size());
        assertEquals("Oswego", result.get(0).getCityName());
        verify(albumRepository).findByCityName("Oswego");
    }

    @Test
    void findByCityNameAndCountryNameShouldReturnMatchingAlbums() {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setAlbumName("Test Album");
        album.setCityName("Oswego");
        album.setCountryName("USA");

        when(albumRepository.findByCityNameAndCountryName("Oswego", "USA"))
                .thenReturn(List.of(album));

        List<Album> result = albumService.findByCityNameAndCountryName("Oswego", "USA");

        assertEquals(1, result.size());
        assertEquals("Oswego", result.get(0).getCityName());
        assertEquals("USA", result.get(0).getCountryName());
        verify(albumRepository).findByCityNameAndCountryName("Oswego", "USA");
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
        savedAlbum.setCityName("Nowhere");
        savedAlbum.setCountryName("Nowhereland");

        when(geocodingService.geocode("Nowhere", "Nowhereland"))
                .thenReturn(Optional.empty());
        when(albumRepository.save(album)).thenReturn(savedAlbum);

        Album result = albumService.create(album);

        assertEquals(ALBUM_ID_2, result.getId());
        assertNull(result.getLat());
        assertNull(result.getLang());
        verify(geocodingService).geocode("Nowhere", "Nowhereland");
        verify(albumRepository).save(album);
    }
}