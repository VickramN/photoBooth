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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlbumServiceTest {

    @Mock
    private AlbumRepository albumRepository;

    @InjectMocks
    private AlbumService albumService;

    @Test
    void findAllShouldReturnAllAlbums() {
        Album album = new Album();
        album.setId(1);
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
        album.setId(1);
        album.setAlbumName("Test Album");

        when(albumRepository.findById(1)).thenReturn(Optional.of(album));

        Optional<Album> result = albumService.findById(1);

        assertTrue(result.isPresent());
        assertEquals("Test Album", result.get().getAlbumName());
        verify(albumRepository).findById(1);
    }

    @Test
    void findByIdShouldReturnEmptyWhenNotFound() {
        when(albumRepository.findById(99)).thenReturn(Optional.empty());

        Optional<Album> result = albumService.findById(99);

        assertTrue(result.isEmpty());
        verify(albumRepository).findById(99);
    }

    @Test
    void createShouldSaveAndReturnAlbum() {
        Album album = new Album();
        album.setAlbumName("Test Album");
        album.setCityName("Oswego");
        album.setCountryName("USA");

        Album savedAlbum = new Album();
        savedAlbum.setId(1);
        savedAlbum.setAlbumName("Test Album");
        savedAlbum.setCityName("Oswego");
        savedAlbum.setCountryName("USA");

        when(albumRepository.save(album)).thenReturn(savedAlbum);

        Album result = albumService.create(album);

        assertEquals(1, result.getId());
        assertEquals("Test Album", result.getAlbumName());
        verify(albumRepository).save(album);
    }

    @Test
    void deleteByIdShouldDeleteAlbum() {
        albumService.deleteById(1);

        verify(albumRepository).deleteById(1);
    }

    @Test
    void findByCityNameShouldReturnMatchingAlbums() {
        Album album = new Album();
        album.setId(1);
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
        album.setId(1);
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
}