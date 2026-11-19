package com.example.photoBooth.api;

import java.util.Set;
import java.util.UUID;

public record ProfileResponse(UUID id, String username, Set<String> roles) {

}
