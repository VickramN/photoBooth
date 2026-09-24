package com.example.photoBooth.controller;

import com.example.photoBooth.api.ImageResponse;
import com.example.photoBooth.entity.Image;
import com.example.photoBooth.entity.User;
import com.example.photoBooth.security.UserPrincipal;
import com.example.photoBooth.service.ImageService;
import com.example.photoBooth.service.ImageUploadResult;
import com.example.photoBooth.service.UploadError;
import com.example.photoBooth.security.JwtService;
import com.example.photoBooth.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImageController.class)
class ImageControllerTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID ALBUM_ID = UUID.randomUUID();
    private static final UUID IMAGE_ID = UUID.randomUUID();
    private static final UUID MISSING_ALBUM_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageService imageService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserRepository userRepository;

    private UserPrincipal principal() {
        User owner = new User();
        owner.setId(OWNER_ID);
        owner.setUsername("owner");
        owner.setPassword("hashed");
        return new UserPrincipal(owner);
    }

    private Image imageWithKey(String key) {
        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setObjectKey(key);
        return image;
    }

    @Test
    void getImagesByAlbumIdShouldReturnImages() throws Exception {
        Image image = imageWithKey("users/x/albums/y/z.jpg");
        when(imageService.findByAlbumId(ALBUM_ID, OWNER_ID)).thenReturn(Optional.of(List.of(image)));
        when(imageService.toResponse(image)).thenReturn(new ImageResponse(IMAGE_ID, ALBUM_ID, "https://presigned.example/z.jpg"));

        mockMvc.perform(get("/albums/" + ALBUM_ID + "/images")
                .with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].url").value("https://presigned.example/z.jpg"));
    }

    @Test
    void getImagesByAlbumIdShouldReturnNotFoundWhenMissingOrNotOwned() throws Exception {
        when(imageService.findByAlbumId(MISSING_ALBUM_ID, OWNER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/albums/" + MISSING_ALBUM_ID + "/images")
                .with(user(principal())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createImageShouldReturnCreatedImageWhenAllChecksPass() throws Exception {
        Image image = imageWithKey("users/x/albums/y/z.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        when(imageService.create(eq(ALBUM_ID), any(), eq(OWNER_ID)))
                .thenReturn(new ImageUploadResult.Success(image));
        when(imageService.toResponse(image))
                .thenReturn(new ImageResponse(IMAGE_ID, ALBUM_ID, "https://presigned.example/z.jpg"));

        mockMvc.perform(multipart("/albums/" + ALBUM_ID + "/images")
                .file(file)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(IMAGE_ID.toString()))
                .andExpect(jsonPath("$.url").value("https://presigned.example/z.jpg"));
    }

    @Test
    void createImageShouldReturnNotFoundWhenAlbumMissingOrNotOwned() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());
        when(imageService.create(eq(MISSING_ALBUM_ID), any(), eq(OWNER_ID)))
                .thenReturn(new ImageUploadResult.Failure(UploadError.ALBUM_NOT_FOUND));

        mockMvc.perform(multipart("/albums/" + MISSING_ALBUM_ID + "/images")
                .file(file)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createImageShouldReturnTooManyRequestsWhenRateLimited() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());
        when(imageService.create(eq(ALBUM_ID), any(), eq(OWNER_ID)))
                .thenReturn(new ImageUploadResult.Failure(UploadError.RATE_LIMITED));

        mockMvc.perform(multipart("/albums/" + ALBUM_ID + "/images")
                .file(file)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
    }

    @Test
    void createImageShouldReturnBadRequestWhenFileTooLarge() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());
        when(imageService.create(eq(ALBUM_ID), any(), eq(OWNER_ID)))
                .thenReturn(new ImageUploadResult.Failure(UploadError.FILE_TOO_LARGE));

        mockMvc.perform(multipart("/albums/" + ALBUM_ID + "/images")
                .file(file)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FILE_TOO_LARGE"));
    }

    @Test
    void createImageShouldReturnBadRequestWhenInvalidImageType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());
        when(imageService.create(eq(ALBUM_ID), any(), eq(OWNER_ID)))
                .thenReturn(new ImageUploadResult.Failure(UploadError.INVALID_IMAGE_TYPE));

        mockMvc.perform(multipart("/albums/" + ALBUM_ID + "/images")
                .file(file)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_IMAGE_TYPE"));
    }

    @Test
    void createImageShouldReturnUnprocessableEntityWhenInfected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());
        when(imageService.create(eq(ALBUM_ID), any(), eq(OWNER_ID)))
                .thenReturn(new ImageUploadResult.Failure(UploadError.INFECTED_FILE));

        mockMvc.perform(multipart("/albums/" + ALBUM_ID + "/images")
                .file(file)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INFECTED_FILE"));
    }

    @Test
    void deleteImageShouldReturnNoContentWhenDeleted() throws Exception {
        when(imageService.deleteByAlbumIdAndImageId(ALBUM_ID, IMAGE_ID, OWNER_ID)).thenReturn(true);

        mockMvc.perform(delete("/albums/" + ALBUM_ID + "/images/" + IMAGE_ID)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteImageShouldReturnNotFoundWhenNotDeleted() throws Exception {
        when(imageService.deleteByAlbumIdAndImageId(ALBUM_ID, IMAGE_ID, OWNER_ID)).thenReturn(false);

        mockMvc.perform(delete("/albums/" + ALBUM_ID + "/images/" + IMAGE_ID)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
