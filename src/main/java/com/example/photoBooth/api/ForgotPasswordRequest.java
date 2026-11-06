package com.example.photoBooth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ForgotPasswordRequest {

    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Za-z0-9._-]+$")

    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}