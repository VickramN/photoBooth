package com.example.photoBooth.controller;

import com.example.photoBooth.entity.Image;
import com.example.photoBooth.service.ImageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImageController.class)
class ImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageService imageService;

    @Test
    void getImagesByAlbumIdShouldReturnImages() throws Exception {
        Image image = new Image();
        image.setId(1);
        image.setImg("test-image-url");

        when(imageService.findByAlbumId(1)).thenReturn(List.of(image));

        mockMvc.perform(get("/albums/1/images")
                .with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].img").value("test-image-url"));

        verify(imageService).findByAlbumId(1);
    }

    @Test
    void createImageShouldReturnCreatedImageWhenAlbumExists() throws Exception {
        Image image = new Image();
        image.setId(1);
        image.setImg("test-image-url");

        when(imageService.create(1, "test-image-url")).thenReturn(Optional.of(image));

        mockMvc.perform(post("/albums/1/images")
                .with(httpBasic("user", "password"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"img\":\"test-image-url\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.img").value("test-image-url"));

        verify(imageService).create(1, "test-image-url");
    }

    @Test
    void createImageShouldReturnNotFoundWhenAlbumMissing() throws Exception {
        when(imageService.create(99, "test-image-url")).thenReturn(Optional.empty());

        mockMvc.perform(post("/albums/99/images")
                .with(httpBasic("user", "password"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"img\":\"test-image-url\"}"))
                .andExpect(status().isNotFound());

        verify(imageService).create(99, "test-image-url");
    }

    @Test
    void deleteImageShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/albums/1/images/2")
                .with(httpBasic("user", "password"))
                .with(csrf()))
                .andExpect(status().isNoContent());

        verify(imageService).deleteByAlbumIdAndImageId(1, 2);
    }
}