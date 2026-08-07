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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlbumController.class)
class AlbumControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlbumService albumService;

    @Test
    void getAlbumsShouldReturnAlbums() throws Exception {
        Album album = new Album();
        album.setId(1);
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
        album.setId(1);
        album.setAlbumName("Test Album");

        when(albumService.findById(1)).thenReturn(Optional.of(album));

        mockMvc.perform(get("/albums/1")
                .with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.albumName").value("Test Album"));

        verify(albumService).findById(1);
    }

    @Test
    void getAlbumByIdShouldReturnNotFoundWhenMissing() throws Exception {
        when(albumService.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/albums/99")
                .with(httpBasic("user", "password")))
                .andExpect(status().isNotFound());

        verify(albumService).findById(99);
    }

    @Test
    void createAlbumShouldReturnCreatedAlbum() throws Exception {
        Album savedAlbum = new Album();
        savedAlbum.setId(1);
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
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.albumName").value("Test Album"));

        verify(albumService).create(any(Album.class));
    }

    @Test
    void deleteAlbumShouldReturnNoContentWhenAlbumExists() throws Exception {
        Album album = new Album();
        album.setId(1);

        when(albumService.findById(1)).thenReturn(Optional.of(album));

        mockMvc.perform(delete("/albums/1")
                .with(httpBasic("user", "password"))
                .with(csrf()))
                .andExpect(status().isNoContent());

        verify(albumService).findById(1);
        verify(albumService).deleteById(1);
    }

    @Test
    void deleteAlbumShouldReturnNotFoundWhenAlbumMissing() throws Exception {
        when(albumService.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/albums/99")
                .with(httpBasic("user", "password"))
                .with(csrf()))
                .andExpect(status().isNotFound());

        verify(albumService).findById(99);
        Mockito.verify(albumService, Mockito.never()).deleteById(99);
    }

    @Test
    void getAlbumsByCityShouldReturnMatchingAlbums() throws Exception {
        Album album = new Album();
        album.setId(1);
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
        album.setId(1);
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