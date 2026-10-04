package com.example.photoBooth.controller;

import com.example.photoBooth.api.SetEnabledRequest;
import com.example.photoBooth.api.UpdateRolesRequest;
import com.example.photoBooth.api.UserResponse;
import com.example.photoBooth.api.ErrorCode;
import com.example.photoBooth.controller.error.ApiException;
import com.example.photoBooth.service.AdminService;
import com.example.photoBooth.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {
        logger.info("GET /admin/users - Fetching all users");
        return adminService.findAllUsers();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        logger.info("GET /admin/users/{} - Fetching user", id);

        return adminService.findUserById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND));
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<UserResponse> updateUserRoles(@PathVariable UUID id,
            @Valid @RequestBody UpdateRolesRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        logger.info("PUT /admin/users/{}/roles - Updating roles to {}", id, request.getRoles());

        try {
            return adminService.updateUserRoles(id, request.getRoles(), principal.getId())
                    .map(ResponseEntity::ok)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid role update for user {}: {}", id, e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UNKNOWN_ROLE);
        }
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<UserResponse> setUserEnabled(@PathVariable UUID id,
            @Valid @RequestBody SetEnabledRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        boolean enabled = request.enabled();
        logger.info("PUT /admin/users/{}/enabled - Setting enabled={}", id, enabled);

        return adminService.setUserEnabled(id, enabled, principal.getId())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        logger.info("DELETE /admin/users/{} - Attempting to delete user", id);

        AdminService.DeleteResult result = adminService.deleteUser(id, principal.getId());

        return switch (result) {
            case DELETED -> ResponseEntity.noContent().build();
            case NOT_FOUND -> throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND);
            case HAS_ALBUMS -> throw new ApiException(HttpStatus.CONFLICT, ErrorCode.USER_HAS_ALBUMS);
        };
    }
}