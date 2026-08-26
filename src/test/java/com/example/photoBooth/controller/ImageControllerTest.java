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
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.eq;

@WebMvcTest(ImageController.class)
class ImageControllerTest {

        private static final UUID ALBUM_ID = UUID.randomUUID();
        private static final UUID IMAGE_ID = UUID.randomUUID();
        private static final UUID MISSING_ALBUM_ID = UUID.randomUUID();

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private ImageService imageService;

        @Test
        void getImagesByAlbumIdShouldReturnImages() throws Exception {
                Image image = new Image();
                image.setId(IMAGE_ID);
                image.setImg("test-image-url");

                when(imageService.findByAlbumId(ALBUM_ID)).thenReturn(List.of(image));

                mockMvc.perform(get("/albums/" + ALBUM_ID + "/images")
                                .with(httpBasic("user", "password")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].img").value("test-image-url"));

                verify(imageService).findByAlbumId(ALBUM_ID);
        }

        @Test
        void createImageShouldReturnCreatedImageWhenAlbumExists() throws Exception {
                Image image = new Image();
                image.setId(IMAGE_ID);
                image.setImg("test-image-url");

                MockMultipartFile file = new MockMultipartFile(
                                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

                when(imageService.create(eq(ALBUM_ID), any())).thenReturn(Optional.of(image));

                mockMvc.perform(multipart("/albums/" + ALBUM_ID + "/images")
                                .file(file)
                                .with(httpBasic("user", "password"))
                                .with(csrf()))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").value(IMAGE_ID.toString()))
                                .andExpect(jsonPath("$.img").value("test-image-url"));

                verify(imageService).create(eq(ALBUM_ID), any());
        }

        @Test
        void createImageShouldReturnNotFoundWhenAlbumMissing() throws Exception {
                MockMultipartFile file = new MockMultipartFile(
                                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

                when(imageService.create(eq(MISSING_ALBUM_ID), any())).thenReturn(Optional.empty());

                mockMvc.perform(multipart("/albums/" + MISSING_ALBUM_ID + "/images")
                                .file(file)
                                .with(httpBasic("user", "password"))
                                .with(csrf()))
                                .andExpect(status().isNotFound());

                verify(imageService).create(eq(MISSING_ALBUM_ID), any());
        }

        @Test
        void deleteImageShouldReturnNoContent() throws Exception {
                mockMvc.perform(delete("/albums/" + ALBUM_ID + "/images/" + IMAGE_ID)
                                .with(httpBasic("user", "password"))
                                .with(csrf()))
                                .andExpect(status().isNoContent());

                verify(imageService).deleteByAlbumIdAndImageId(ALBUM_ID, IMAGE_ID);
        }
}