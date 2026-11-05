package com.example.photoBooth.controller;

import com.example.photoBooth.api.AuthResponse;
import com.example.photoBooth.api.ForgotPasswordRequest;
import com.example.photoBooth.api.ResetPasswordRequest;
import com.example.photoBooth.security.RateLimit;
import com.example.photoBooth.security.RequestRateLimiter;
import com.example.photoBooth.controller.error.ApiException;
import com.example.photoBooth.api.LoginRequest;
import com.example.photoBooth.api.RegisterRequest;
import com.example.photoBooth.service.AuthService;
import com.example.photoBooth.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    private final RequestRateLimiter requestRateLimiter;

    public AuthController(AuthService authService, RequestRateLimiter requestRateLimiter) {
        this.authService = authService;
        this.requestRateLimiter = requestRateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        requestRateLimiter.consume(RateLimit.REGISTER_IP, httpRequest.getRemoteAddr());
        logger.info("POST /auth/register - Registering user {}", request.getUsername());

        try {
            authService.register(request.getUsername(), request.getPassword());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (IllegalArgumentException e) {
            logger.warn("Registration failed for {}: {}", request.getUsername(), e.getMessage());
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.USERNAME_TAKEN);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        requestRateLimiter.consume(RateLimit.LOGIN_IP, httpRequest.getRemoteAddr());
        requestRateLimiter.requireAvailable(RateLimit.LOGIN_FAILURES_USERNAME, request.getUsername());
        logger.info("POST /auth/login - Login attempt for {}", request.getUsername());

        try {
            String token = authService.login(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(new AuthResponse(token));
        } catch (AuthenticationException e) {
            requestRateLimiter.recordFailure(RateLimit.LOGIN_FAILURES_USERNAME, request.getUsername());
            logger.warn("Login failed for {}: {}", request.getUsername(), e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        requestRateLimiter.consume(RateLimit.FORGOT_PASSWORD_IP, httpRequest.getRemoteAddr());
        requestRateLimiter.consume(RateLimit.FORGOT_PASSWORD_USERNAME, request.getUsername());
        logger.info("POST /auth/forgot-password - Request for {}", request.getUsername());
        authService.requestPasswordReset(request.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        logger.info("POST /auth/reset-password - Attempting reset");
        try {
            authService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            logger.warn("Password reset failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_RESET_TOKEN);
        }
    }
}