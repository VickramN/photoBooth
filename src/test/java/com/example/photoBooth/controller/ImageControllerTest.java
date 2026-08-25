package com.example.photoBooth.controller;

import com.example.photoBooth.entity.Image;
import com.example.photoBooth.service.ImageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        when(imageService.create(eq(1), any())).thenReturn(Optional.of(image));

        mockMvc.perform(multipart("/albums/1/images")
                .file(file)
                .with(httpBasic("user", "password"))
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.img").value("test-image-url"));

        verify(imageService).create(eq(1), any());
    }

    @Test
    void createImageShouldReturnNotFoundWhenAlbumMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        when(imageService.create(eq(99), any())).thenReturn(Optional.empty());

        mockMvc.perform(multipart("/albums/99/images")
                .file(file)
                .with(httpBasic("user", "password"))
                .with(csrf()))
                .andExpect(status().isNotFound());

        verify(imageService).create(eq(99), any());
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