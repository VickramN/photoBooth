package com.example.photoBooth.controller;

import com.example.photoBooth.api.ProfileResponse;
import com.example.photoBooth.security.UserPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    @GetMapping
    public ProfileResponse getProfile(@AuthenticationPrincipal UserPrincipal principal) {

        Set<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return new ProfileResponse(principal.getId(), principal.getUsername(), roles);

    }

}
