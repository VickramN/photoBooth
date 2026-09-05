package com.example.photoBooth.controller;

import com.example.photoBooth.api.UpdateRolesRequest;
import com.example.photoBooth.api.UserResponse;
import com.example.photoBooth.service.AdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
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
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<UserResponse> updateUserRoles(@PathVariable UUID id,
                                                         @RequestBody UpdateRolesRequest request) {
        logger.info("PUT /admin/users/{}/roles - Updating roles to {}", id, request.getRoles());

        try {
            return adminService.updateUserRoles(id, request.getRoles())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid role update for user {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<UserResponse> setUserEnabled(@PathVariable UUID id,
                                                        @RequestBody Map<String, Boolean> request) {
        boolean enabled = request.getOrDefault("enabled", true);
        logger.info("PUT /admin/users/{}/enabled - Setting enabled={}", id, enabled);

        return adminService.setUserEnabled(id, enabled)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        logger.info("DELETE /admin/users/{} - Attempting to delete user", id);

        AdminService.DeleteResult result = adminService.deleteUser(id);

        return switch (result) {
            case DELETED -> ResponseEntity.noContent().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case HAS_ALBUMS -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
    }
}