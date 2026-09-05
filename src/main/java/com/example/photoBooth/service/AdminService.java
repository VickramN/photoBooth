package com.example.photoBooth.service;

import com.example.photoBooth.api.UserResponse;
import com.example.photoBooth.entity.Role;
import com.example.photoBooth.entity.User;
import com.example.photoBooth.repository.AlbumRepository;
import com.example.photoBooth.repository.RoleRepository;
import com.example.photoBooth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AlbumRepository albumRepository;

    public AdminService(UserRepository userRepository, RoleRepository roleRepository,
                         AlbumRepository albumRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.albumRepository = albumRepository;
    }

    public List<UserResponse> findAllUsers() {
        logger.info("Admin: fetching all users");
        return userRepository.findAll().stream()
                .map(UserResponse::new)
                .collect(Collectors.toList());
    }

    public Optional<UserResponse> findUserById(UUID id) {
        logger.info("Admin: fetching user {}", id);
        return userRepository.findById(id).map(UserResponse::new);
    }

    public Optional<UserResponse> updateUserRoles(UUID id, Set<String> roleNames) {
        Optional<User> optionalUser = userRepository.findById(id);

        if (optionalUser.isEmpty()) {
            logger.warn("Admin: cannot update roles, user not found: {}", id);
            return Optional.empty();
        }

        Set<Role> roles = roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + name)))
                .collect(Collectors.toSet());

        User user = optionalUser.get();
        user.setRoles(roles);
        User savedUser = userRepository.save(user);

        logger.info("Admin: updated roles for user {} to {}", id, roleNames);
        return Optional.of(new UserResponse(savedUser));
    }

    public Optional<UserResponse> setUserEnabled(UUID id, boolean enabled) {
        Optional<User> optionalUser = userRepository.findById(id);

        if (optionalUser.isEmpty()) {
            logger.warn("Admin: cannot update enabled status, user not found: {}", id);
            return Optional.empty();
        }

        User user = optionalUser.get();
        user.setEnabled(enabled);
        User savedUser = userRepository.save(user);

        logger.info("Admin: set user {} enabled={}", id, enabled);
        return Optional.of(new UserResponse(savedUser));
    }

    public DeleteResult deleteUser(UUID id) {
        Optional<User> optionalUser = userRepository.findById(id);

        if (optionalUser.isEmpty()) {
            logger.warn("Admin: cannot delete, user not found: {}", id);
            return DeleteResult.NOT_FOUND;
        }

        boolean hasAlbums = !albumRepository.findByOwner_Id(id).isEmpty();

        if (hasAlbums) {
            logger.warn("Admin: refusing to delete user {} - owns existing albums", id);
            return DeleteResult.HAS_ALBUMS;
        }

        userRepository.deleteById(id);
        logger.info("Admin: deleted user {}", id);
        return DeleteResult.DELETED;
    }

    public enum DeleteResult {
        DELETED, NOT_FOUND, HAS_ALBUMS
    }
}