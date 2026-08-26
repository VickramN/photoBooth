package com.example.photoBooth.controller;

import com.example.photoBooth.entity.Album;
import com.example.photoBooth.service.AlbumService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlbumController.class)
class AlbumControllerTest {

    private static final UUID ALBUM_ID = UUID.randomUUID();
    private static final UUID MISSING_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlbumService albumService;

    @Test
    void getAlbumsShouldReturnAlbums() throws Exception {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setAlbumName("Test Album");
        album.setCityName("Oswego");
        album.setCountryName("USA");

        when(albumService.findAll()).thenReturn(List.of(album));

        mockMvc.perform(get("/albums")
                .with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].albumName").value("Test Album"));

        verify(albumService).findAll();
    }

    @Test
    void getAlbumByIdShouldReturnAlbumWhenFound() throws Exception {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setAlbumName("Test Album");

        when(albumService.findById(ALBUM_ID)).thenReturn(Optional.of(album));

        mockMvc.perform(get("/albums/" + ALBUM_ID)
                .with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.albumName").value("Test Album"));

        verify(albumService).findById(ALBUM_ID);
    }

    @Test
    void getAlbumByIdShouldReturnNotFoundWhenMissing() throws Exception {
        when(albumService.findById(MISSING_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/albums/" + MISSING_ID)
                .with(httpBasic("user", "password")))
                .andExpect(status().isNotFound());

        verify(albumService).findById(MISSING_ID);
    }

    @Test
    void createAlbumShouldReturnCreatedAlbum() throws Exception {
        Album savedAlbum = new Album();
        savedAlbum.setId(ALBUM_ID);
        savedAlbum.setAlbumName("Test Album");
        savedAlbum.setCityName("Oswego");
        savedAlbum.setCountryName("USA");

        when(albumService.create(any(Album.class))).thenReturn(savedAlbum);

        mockMvc.perform(post("/albums")
                .with(httpBasic("user", "password"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"albumName\":\"Test Album\",\"cityName\":\"Oswego\",\"countryName\":\"USA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ALBUM_ID.toString()))
                .andExpect(jsonPath("$.albumName").value("Test Album"));

        verify(albumService).create(any(Album.class));
    }

    @Test
    void deleteAlbumShouldReturnNoContentWhenAlbumExists() throws Exception {
        Album album = new Album();
        album.setId(ALBUM_ID);

        when(albumService.findById(ALBUM_ID)).thenReturn(Optional.of(album));

        mockMvc.perform(delete("/albums/" + ALBUM_ID)
                .with(httpBasic("user", "password"))
                .with(csrf()))
                .andExpect(status().isNoContent());

        verify(albumService).findById(ALBUM_ID);
        verify(albumService).deleteById(ALBUM_ID);
    }

    @Test
    void deleteAlbumShouldReturnNotFoundWhenAlbumMissing() throws Exception {
        when(albumService.findById(MISSING_ID)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/albums/" + MISSING_ID)
                .with(httpBasic("user", "password"))
                .with(csrf()))
                .andExpect(status().isNotFound());

        verify(albumService).findById(MISSING_ID);
        Mockito.verify(albumService, Mockito.never()).deleteById(MISSING_ID);
    }

    @Test
    void getAlbumsByCityShouldReturnMatchingAlbums() throws Exception {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setAlbumName("Test Album");
        album.setCityName("Oswego");
        album.setCountryName("USA");

        when(albumService.findByCityName("Oswego")).thenReturn(List.of(album));

        mockMvc.perform(get("/albums")
                .param("city", "Oswego")
                .with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cityName").value("Oswego"));

        verify(albumService).findByCityName("Oswego");
    }

    @Test
    void getAlbumsByCityAndCountryShouldReturnMatchingAlbums() throws Exception {
        Album album = new Album();
        album.setId(ALBUM_ID);
        album.setAlbumName("Test Album");
        album.setCityName("Oswego");
        album.setCountryName("USA");

        when(albumService.findByCityNameAndCountryName("Oswego", "USA"))
                .thenReturn(List.of(album));

        mockMvc.perform(get("/albums")
                .param("city", "Oswego")
                .param("country", "USA")
                .with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cityName").value("Oswego"))
                .andExpect(jsonPath("$[0].countryName").value("USA"));

        verify(albumService).findByCityNameAndCountryName("Oswego", "USA");
    }
}