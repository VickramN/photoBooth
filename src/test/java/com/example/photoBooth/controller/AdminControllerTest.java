package com.example.photoBooth.controller;

import com.example.photoBooth.service.AdminService;
import com.example.photoBooth.service.SelfModificationException;
import com.example.photoBooth.security.JwtService;
import com.example.photoBooth.security.UserPrincipal;
import com.example.photoBooth.repository.UserRepository;
import com.example.photoBooth.api.UserResponse;
import com.example.photoBooth.config.SecurityConfig;
import com.example.photoBooth.entity.User;
import com.example.photoBooth.entity.Role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.Set;
import java.util.List;
import java.util.Optional;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
public class AdminControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User buildUser(UUID id, String roleName) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + roleName);
        user.setPassword("hashed");
        user.setRoles(Set.of(new Role(roleName)));
        return user;
    }

    private UserPrincipal principalWithRole(UUID id, String roleName) {
        return new UserPrincipal(buildUser(id, roleName));
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/users")
                .with(user(principalWithRole(USER_ID, "ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verifyNoInteractions(adminService);
    }

    @Test
    void adminCanListUsers() throws Exception {
        when(adminService.findAllUsers()).thenReturn(List.of());

        mockMvc.perform(get("/admin/users")
                .with(user(principalWithRole(ADMIN_ID, "ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void findUserByIdReturnsNotFoundWhenUserDoesNotExist() throws Exception {
        when(adminService.findUserById(USER_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/users/" + USER_ID)
                .with(user(principalWithRole(ADMIN_ID, "ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    @Test
    void setUserEnabledPassesActingAdminId() throws Exception {
        when(adminService.setUserEnabled(USER_ID, false, ADMIN_ID))
                .thenReturn(Optional.of(new UserResponse(buildUser(USER_ID, "ROLE_USER"))));

        mockMvc.perform(put("/admin/users/" + USER_ID + "/enabled")
                .with(user(principalWithRole(ADMIN_ID, "ROLE_ADMIN")))
                .contentType("application/json")
                .content("{\"enabled\": false}"))
                .andExpect(status().isOk());

        verify(adminService).setUserEnabled(USER_ID, false, ADMIN_ID);
    }

    @Test
    void updateUserRolesReturnsConflictWhenAdminModifiesSelf() throws Exception {
        when(adminService.updateUserRoles(ADMIN_ID, Set.of("ROLE_USER"), ADMIN_ID))
                .thenThrow(new SelfModificationException("self-modification"));

        mockMvc.perform(put("/admin/users/" + ADMIN_ID + "/roles")
                .with(user(principalWithRole(ADMIN_ID, "ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\": [\"ROLE_USER\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CANNOT_MODIFY_SELF"));
    }

    @Test
    void updateUserRolesReturnsUnknownRoleWhenRoleDoesNotExist() throws Exception {
        when(adminService.updateUserRoles(USER_ID, Set.of("ROLE_WIZARD"), ADMIN_ID))
                .thenThrow(new IllegalArgumentException("Unknown role: ROLE_WIZARD"));

        mockMvc.perform(put("/admin/users/" + USER_ID + "/roles")
                .with(user(principalWithRole(ADMIN_ID, "ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\": [\"ROLE_WIZARD\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNKNOWN_ROLE"));
    }

    @Test
    void wrongContentTypeReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(put("/admin/users/" + USER_ID + "/enabled")
                .with(user(principalWithRole(ADMIN_ID, "ROLE_ADMIN")))
                .contentType(MediaType.TEXT_PLAIN)
                .content("enabled=false"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(adminService);
    }

    @Test
    void setUserEnabledReturnsBadRequestWhenFieldMispelled() throws Exception {
        mockMvc.perform(put("/admin/users/" + USER_ID + "/enabled")
                .with(user(principalWithRole(ADMIN_ID, "ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enable\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.enabled").exists());

        verifyNoInteractions(adminService);
    }

    @Test
    void deleteUserReturnsConflictWhenUserHasAlbums() throws Exception {

        when(adminService.deleteUser(USER_ID, ADMIN_ID))
                .thenReturn(AdminService.DeleteResult.HAS_ALBUMS);

        mockMvc.perform(delete("/admin/users/" + USER_ID)
                .with(user(principalWithRole(ADMIN_ID, "ROLE_ADMIN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USER_HAS_ALBUMS"));
    }

}
