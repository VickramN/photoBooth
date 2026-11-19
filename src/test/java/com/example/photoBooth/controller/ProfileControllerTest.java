package com.example.photoBooth.controller;

import com.example.photoBooth.config.SecurityConfig;
import com.example.photoBooth.entity.Role;
import com.example.photoBooth.entity.User;
import com.example.photoBooth.repository.UserRepository;
import com.example.photoBooth.security.JwtService;
import com.example.photoBooth.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
class ProfileControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private UserPrincipal principal() {
        Role role = new Role();
        role.setName("ROLE_USER");
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("alice");
        user.setPassword("hashed");
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        return new UserPrincipal(user);
    }

    @Test
    void getProfileReturnsCurrentUser() throws Exception {
        mockMvc.perform(get("/profile").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.enabled").doesNotExist());
    }

    @Test
    void getProfileReturnsUnauthorizedWhenLoggedOut() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }
}