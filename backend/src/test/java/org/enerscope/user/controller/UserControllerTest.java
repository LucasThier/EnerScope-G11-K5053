package org.enerscope.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.common.ForbiddenException;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.dto.RoleLevel;
import org.enerscope.user.dto.UpdatePlatformRoleRequestDTO;
import org.enerscope.user.dto.UserRoleDTO;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.service.UserRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint tests for {@link UserController}. Uses {@code @WebMvcTest} (web
 * layer only, no database) with the real {@link SecurityConfig}/
 * {@link AuthFilter} so every request goes through the actual JWT filter
 * chain, like {@code OrganizationControllerTest}. {@link UserRoleService} is
 * mocked.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class UserControllerTest {

    private static final String ACCESS_TOKEN = "access-token-xyz";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRoleService userRoleService;
    @MockitoBean
    private SessionService sessionService;
    // GlobalExceptionHandler (@ControllerAdvice) needs an AppLogger bean to load.
    @MockitoBean
    private AppLogger logger;

    @BeforeEach
    void setUp() {
        User caller = User.fromJwtClaims(UUID.randomUUID(), "jane@enerscope.org", "Jane", "Doe");
        Session session = new Session(ACCESS_TOKEN, caller, Instant.now().plusSeconds(3600));
        when(sessionService.validate(ACCESS_TOKEN)).thenReturn(Optional.of(session));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void listRolesReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRoleService.listRoles(userId)).thenReturn(List.of(
                new UserRoleDTO(RoleLevel.PLATFORM, null, null, "USER")));

        mockMvc.perform(get("/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].level").value("PLATFORM"))
                .andExpect(jsonPath("$.data[0].roleType").value("USER"));
    }

    @Test
    void listRolesRequiresAuthenticationWith401() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/users/" + userId + "/roles"))
                .andExpect(status().isUnauthorized());

        verify(userRoleService, never()).listRoles(any());
    }

    @Test
    void listRolesPropagatesForbiddenWith403() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRoleService.listRoles(userId))
                .thenThrow(new ForbiddenException("You are not allowed to view this user's roles"));

        mockMvc.perform(get("/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void listRolesPropagatesUnknownUserWith400() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRoleService.listRoles(userId)).thenThrow(new IllegalArgumentException("User not found"));

        mockMvc.perform(get("/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    // ---- assignPlatformRole -------------------------------------------------

    @Test
    void assignPlatformRoleReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        User updated = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.ADMIN);
        when(userRoleService.assignPlatformRole(eq(userId), eq(PlatformRole.ADMIN))).thenReturn(updated);

        mockMvc.perform(patch("/users/" + userId + "/platform-role")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdatePlatformRoleRequestDTO(PlatformRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void assignPlatformRoleRequiresAuthenticationWith401() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/users/" + userId + "/platform-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdatePlatformRoleRequestDTO(PlatformRole.ADMIN))))
                .andExpect(status().isUnauthorized());

        verify(userRoleService, never()).assignPlatformRole(any(), any());
    }

    @Test
    void assignPlatformRolePropagatesForbiddenWith403() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRoleService.assignPlatformRole(eq(userId), eq(PlatformRole.ADMIN)))
                .thenThrow(new ForbiddenException("Only a platform admin can assign platform roles"));

        mockMvc.perform(patch("/users/" + userId + "/platform-role")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdatePlatformRoleRequestDTO(PlatformRole.ADMIN))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void assignPlatformRoleRejectsInvalidBodyWithValidationError() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/users/" + userId + "/platform-role")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(userRoleService, never()).assignPlatformRole(any(), any());
    }
}
