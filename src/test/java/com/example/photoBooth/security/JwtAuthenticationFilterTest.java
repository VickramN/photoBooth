package com.example.photoBooth.security;

import com.example.photoBooth.entity.User;
import com.example.photoBooth.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-that-is-at-least-32-bytes-long");
        properties.setExpirationMs(60_000);
        jwtService = new JwtService(properties);
        filter = new JwtAuthenticationFilter(jwtService, userRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User user(boolean enabled, Instant passwordChangedAt) {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("alice");
        user.setPassword("hashed");
        user.setEnabled(enabled);
        user.setPasswordChangedAt(passwordChangedAt);
        return user;
    }

    private MockFilterChain runFilterWith(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/albums");
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    void enabledUserWithValidTokenShouldBeAuthenticated() throws Exception {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user(true, Instant.now().minus(1, ChronoUnit.HOURS))));

        MockFilterChain chain = runFilterWith(jwtService.generateToken("alice", USER_ID));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(USER_ID, ((UserPrincipal) authentication.getPrincipal()).getId());
        assertNotNull(chain.getRequest()); // request continued down the chain
    }

    @Test
    void disabledUserWithValidTokenShouldNotBeAuthenticated() throws Exception {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user(false, Instant.now().minus(1, ChronoUnit.HOURS))));

        MockFilterChain chain = runFilterWith(jwtService.generateToken("alice", USER_ID));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest()); // still continues, so security rules can reject it
    }

    @Test
    void tokenIssuedBeforePasswordChangeShouldNotBeAuthenticated() throws Exception {
        String oldToken = jwtService.generateToken("alice", USER_ID);
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user(true, Instant.now().plus(1, ChronoUnit.HOURS))));

        runFilterWith(oldToken);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}