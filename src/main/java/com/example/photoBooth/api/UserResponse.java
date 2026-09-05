package com.example.photoBooth.api;

import com.example.photoBooth.entity.User;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class UserResponse {

    private UUID id;
    private String username;
    private Set<String> roles;
    private boolean enabled;

    public UserResponse(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.roles = user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toSet());
        this.enabled = user.isEnabled();
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public boolean isEnabled() {
        return enabled;
    }
}