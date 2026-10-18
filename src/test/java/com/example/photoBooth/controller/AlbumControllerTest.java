package com.example.photoBooth.controller;

import com.example.photoBooth.api.ImageResponse;
import com.example.photoBooth.config.SecurityConfig;
import com.example.photoBooth.entity.Album;
import com.example.photoBooth.entity.Image;
import com.example.photoBooth.entity.User;
import com.example.photoBooth.security.UserPrincipal;
import com.example.photoBooth.service.AlbumService;
import com.example.photoBooth.service.ImageService;
import com.example.photoBooth.security.JwtService;
import com.example.photoBooth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlbumController.class)
@Import(SecurityConfig.class)
class AlbumControllerTest {

        private static final UUID OWNER_ID = UUID.randomUUID();
        private static final UUID ALBUM_ID = UUID.randomUUID();
        private static final UUID MISSING_ID = UUID.randomUUID();

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AlbumService albumService;

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

        @Test
        void getAlbumsShouldReturnAlbums() throws Exception {
                Album album = new Album();
                album.setId(ALBUM_ID);
                album.setAlbumName("Test Album");
                album.setCityName("Oswego");
                album.setCountryName("USA");

                when(albumService.findAll(OWNER_ID)).thenReturn(List.of(album));

                mockMvc.perform(get("/albums")
                                .with(user(principal())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].albumName").value("Test Album"));

                verify(albumService).findAll(OWNER_ID);
        }

        @Test
        void getAlbumByIdShouldReturnAlbumWhenFound() throws Exception {
                Album album = new Album();
                album.setId(ALBUM_ID);
                album.setAlbumName("Test Album");

                when(albumService.findById(ALBUM_ID, OWNER_ID)).thenReturn(Optional.of(album));

                mockMvc.perform(get("/albums/" + ALBUM_ID)
                                .with(user(principal())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.albumName").value("Test Album"));

                verify(albumService).findById(ALBUM_ID, OWNER_ID);
        }

        @Test
        void getAlbumByIdShouldReturnNotFoundWhenMissingOrNotOwned() throws Exception {
                when(albumService.findById(MISSING_ID, OWNER_ID)).thenReturn(Optional.empty());

                mockMvc.perform(get("/albums/" + MISSING_ID)
                                .with(user(principal())))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").value("ALBUM_NOT_FOUND"));

                verify(albumService).findById(MISSING_ID, OWNER_ID);
        }

        @Test
        void createAlbumShouldReturnCreatedAlbum() throws Exception {
                Album savedAlbum = new Album();
                savedAlbum.setId(ALBUM_ID);
                savedAlbum.setAlbumName("Test Album");
                savedAlbum.setCityName("Oswego");
                savedAlbum.setCountryName("USA");

                when(albumService.create(any(Album.class), org.mockito.ArgumentMatchers.eq(OWNER_ID)))
                                .thenReturn(savedAlbum);

                mockMvc.perform(post("/albums")
                                .with(user(principal()))
                                .contentType("application/json")
                                .content("{\"albumName\":\"Test Album\",\"cityName\":\"Oswego\",\"countryName\":\"USA\"}"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").value(ALBUM_ID.toString()))
                                .andExpect(jsonPath("$.albumName").value("Test Album"));

                verify(albumService).create(any(Album.class), org.mockito.ArgumentMatchers.eq(OWNER_ID));
        }

        @Test
        void createAlbumShouldReturnBadRequestWhenNameBlank() throws Exception {

                mockMvc.perform(post("/albums")
                                .with(user(principal()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"albumName\":\"\",\"cityName\":\"Oswego\",\"countryName\":\"USA\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                                .andExpect(jsonPath("$.fields.albumName").exists())
                                .andExpect(jsonPath("$.fields.cityName").doesNotExist())
                                .andExpect(jsonPath("$.fields.countryName").doesNotExist());

                verifyNoInteractions(albumService);
        }

        @Test
        void deleteAlbumShouldReturnNoContentWhenDeleted() throws Exception {
                when(albumService.deleteById(ALBUM_ID, OWNER_ID)).thenReturn(true);

                mockMvc.perform(delete("/albums/" + ALBUM_ID)
                                .with(user(principal())))
                                .andExpect(status().isNoContent());

                verify(albumService).deleteById(ALBUM_ID, OWNER_ID);
        }

        @Test
        void deleteAlbumShouldReturnNotFoundWhenNotDeleted() throws Exception {
                when(albumService.deleteById(MISSING_ID, OWNER_ID)).thenReturn(false);

                mockMvc.perform(delete("/albums/" + MISSING_ID)
                                .with(user(principal())))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").value("ALBUM_NOT_FOUND"));

                verify(albumService).deleteById(MISSING_ID, OWNER_ID);
        }

        @Test
        void getAlbumsByCityShouldReturnMatchingAlbums() throws Exception {
                Album album = new Album();
                album.setId(ALBUM_ID);
                album.setAlbumName("Test Album");
                album.setCityName("Oswego");
                album.setCountryName("USA");

                when(albumService.findByCityName("Oswego", OWNER_ID)).thenReturn(List.of(album));

                mockMvc.perform(get("/albums")
                                .param("city", "Oswego")
                                .with(user(principal())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].cityName").value("Oswego"));

                verify(albumService).findByCityName("Oswego", OWNER_ID);
        }

        @Test
        void getAlbumsByCityAndCountryShouldReturnMatchingAlbums() throws Exception {
                Album album = new Album();
                album.setId(ALBUM_ID);
                album.setAlbumName("Test Album");
                album.setCityName("Oswego");
                album.setCountryName("USA");

                when(albumService.findByCityNameAndCountryName("Oswego", "USA", OWNER_ID))
                                .thenReturn(List.of(album));

                mockMvc.perform(get("/albums")
                                .param("city", "Oswego")
                                .param("country", "USA")
                                .with(user(principal())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].cityName").value("Oswego"))
                                .andExpect(jsonPath("$[0].countryName").value("USA"));

                verify(albumService).findByCityNameAndCountryName("Oswego", "USA", OWNER_ID);
        }

        @Test
        void getAlbumsByCountryShouldReturnMatchingAlbums() throws Exception {
                Album album = new Album();
                album.setId(ALBUM_ID);
                album.setAlbumName("Test Album");
                album.setCityName("Oswego");
                album.setCountryName("USA");

                when(albumService.findByCountryName("USA", OWNER_ID))
                                .thenReturn(List.of(album));

                mockMvc.perform(get("/albums")
                                .param("country", "USA")
                                .with(user(principal())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].countryName").value("USA"));

                verify(albumService).findByCountryName("USA", OWNER_ID);
        }

        @Test
        void getAlbumsShouldReturnForbiddenWhenUnauthenticated() throws Exception {

                mockMvc.perform(get("/albums"))
                                .andExpect(status().isForbidden());

                verifyNoInteractions(albumService);
        }

        @Test
        void getAlbumSummariesShouldReturnCountAndSingleCover() throws Exception {
                Album album = new Album();
                album.setId(ALBUM_ID);
                album.setAlbumName("Paris Trip");
                album.setCityName("Paris");
                album.setCountryName("France");
                album.setLat(48.85);
                album.setLang(2.35);

                Image first = new Image();
                first.setId(UUID.randomUUID());
                first.setObjectKey("first.jpg");
                Image second = new Image();
                second.setId(UUID.randomUUID());
                second.setObjectKey("second.jpg");
                album.getImages().add(first);
                album.getImages().add(second);

                when(albumService.findAll(OWNER_ID)).thenReturn(List.of(album));
                when(imageService.toResponse(first))
                                .thenReturn(new ImageResponse(first.getId(), ALBUM_ID,
                                                "https://presigned.example/first.jpg"));

                mockMvc.perform(get("/albums/summary")
                                .with(user(principal())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].imageCount").value(2))
                                .andExpect(jsonPath("$[0].coverUrl").value("https://presigned.example/first.jpg"))
                                .andExpect(jsonPath("$[0].lat").value(48.85))
                                .andExpect(jsonPath("$[0].images").doesNotExist());

                verify(imageService, times(1)).toResponse(any());
        }

        @Test
        void getAlbumSummariesShouldReturnNullsForEmptyUnplacedAlbum() throws Exception {
                Album album = new Album();
                album.setId(ALBUM_ID);
                album.setAlbumName("Mystery Trip");
                album.setCityName("Nowhere");
                album.setCountryName("Nowhereland");

                when(albumService.findAll(OWNER_ID)).thenReturn(List.of(album));

                mockMvc.perform(get("/albums/summary")
                                .with(user(principal())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].imageCount").value(0))
                                .andExpect(jsonPath("$[0].coverUrl").value(nullValue()))
                                .andExpect(jsonPath("$[0].lat").value(nullValue()))
                                .andExpect(jsonPath("$[0].lang").value(nullValue()));

                verifyNoInteractions(imageService);
        }
}