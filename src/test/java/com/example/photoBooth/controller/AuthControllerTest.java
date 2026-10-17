package com.example.photoBooth.controller;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.photoBooth.config.SecurityConfig;
import com.example.photoBooth.repository.UserRepository;
import com.example.photoBooth.security.JwtService;
import com.example.photoBooth.service.AuthService;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void registerReturnsCreatedWhenRequestIsValid() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"newuser\", \"password\": \"SomePass123!\"}"))
                .andExpect(status().isCreated());

        verify(authService).register("newuser", "SomePass123!");
    }

    @Test
    void registerReturnsConflictErrorWhenUsernameIsTaken() throws Exception {

        doThrow(new IllegalArgumentException("Username already taken: newuser"))
                .when(authService).register("newuser", "SomePass123!");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"newuser\", \"password\": \"SomePass123!\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USERNAME_TAKEN"));
    }

    @Test
    void registerReturnsBadRequestWhenPasswordMissing() throws Exception {

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"someone\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.password").exists())
                .andExpect(jsonPath("$.fields.username").doesNotExist());

        verifyNoInteractions(authService);
    }

    @Test
    void loginReturnsSuccessWhenValidLogin() throws Exception {
        when(authService.login("alice", "SomePass!23"))
                .thenReturn("test-token");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"alice\", \"password\": \"SomePass!23\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-token"));

    }

    @Test
    void loginReturnsUnauthorizedWhenInvalidPassword() throws Exception {
        when(authService.login("alice", "wrongPassword"))
                .thenThrow(new BadCredentialsException("Bad Credentials"));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"alice\", \"password\": \"wrongPassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));

    }

    @Test
    void loginReturnsUnauthorizedWhenUserDisabled() throws Exception {
        when(authService.login("alice", "SomePass!23"))
                .thenThrow(new DisabledException("User is disabled"));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"alice\", \"password\": \"SomePass!23\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));

    }

    @Test
    void resetPasswordReturnsBadRequestWhenInvalidResetToken() throws Exception {
        doThrow(new IllegalArgumentException("Invalid or expired reset token"))
                .when(authService).resetPassword("invalid-token", "newPassword");

        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"token\": \"invalid-token\", \"newPassword\": \"newPassword\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void forgotPasswordReturnsSuccessWhenUnknownUser() throws Exception {

        mockMvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"nobody\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(authService).requestPasswordReset("nobody");
    }

}
