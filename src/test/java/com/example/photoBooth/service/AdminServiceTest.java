package com.example.photoBooth.service;

import com.example.photoBooth.entity.Album;
import com.example.photoBooth.entity.Role;
import com.example.photoBooth.entity.User;
import com.example.photoBooth.repository.AlbumRepository;
import com.example.photoBooth.repository.PasswordResetTokenRepository;
import com.example.photoBooth.repository.RoleRepository;
import com.example.photoBooth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AlbumRepository albumRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;

    private AdminService adminService;

    @BeforeEach
    void setup(){
        adminService = new AdminService(userRepository,roleRepository,albumRepository,passwordResetTokenRepository);
    }

    @Test 
    void deleteUser_deleteResetTokensBeforeUser(){
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
        when(albumRepository.findByOwner_Id(USER_ID)).thenReturn(List.of());

        AdminService.DeleteResult result = adminService.deleteUser(USER_ID);

        assertEquals(AdminService.DeleteResult.DELETED, result);
        InOrder inOrder = inOrder(passwordResetTokenRepository, userRepository);
        inOrder.verify(passwordResetTokenRepository).deleteByUser_Id(USER_ID);
        inOrder.verify(userRepository).deleteById(USER_ID);
    }
    
    @Test 
    void deleteUser_withAlbums_refusesAndLeavesTokensAlone(){
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
        when(albumRepository.findByOwner_Id(USER_ID)).thenReturn(List.of(new Album()));

        AdminService.DeleteResult result = adminService.deleteUser(USER_ID);

        assertEquals(AdminService.DeleteResult.HAS_ALBUMS, result);
        verify(passwordResetTokenRepository, never()).deleteByUser_Id(any());
        verify(userRepository, never()).deleteById(any());
        
    }
}
