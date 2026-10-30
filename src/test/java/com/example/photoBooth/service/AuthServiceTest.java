package com.example.photoBooth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.photoBooth.entity.PasswordResetToken;
import com.example.photoBooth.entity.Role;
import com.example.photoBooth.entity.User;
import com.example.photoBooth.repository.PasswordResetTokenRepository;
import com.example.photoBooth.repository.RoleRepository;
import com.example.photoBooth.repository.UserRepository;
import com.example.photoBooth.security.JwtService;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordResetNotifier passwordResetNotifier;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, passwordResetTokenRepository, passwordEncoder,
                authenticationManager, jwtService, passwordResetNotifier);
    }

    @Test
    void register_whenInsertLosesRace_throwsUsernameTaken() {
        when(userRepository.findByUsername("race-user")).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(new Role("ROLE_USER")));
        when(passwordEncoder.encode("pw")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(IllegalArgumentException.class,
                () -> authService.register("race-user", "pw"));
    }

    @Test
    void requestPasswordResetShouldSendTokenMatchingStoredHash() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        authService.requestPasswordReset("alice");

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetNotifier).sendResetToken(eq(user), tokenCaptor.capture());

        ArgumentCaptor<PasswordResetToken> savedCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(savedCaptor.capture());

        String expectedHash = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(tokenCaptor.getValue().getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedHash, savedCaptor.getValue().getTokenHash());
    }

    @Test
    void requestPasswordResetShouldNotSendAnythingForUnknownUser() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        authService.requestPasswordReset("nobody");

        verifyNoInteractions(passwordResetNotifier);
    }

}
