package org.enerscope.role.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.role.dto.RoleAssignmentRequestDTO;
import org.enerscope.role.dto.RoleLevel;
import org.enerscope.role.dto.UserRoleDTO;
import org.enerscope.role.service.RoleService;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.model.User;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint tests for {@link UserRoleController}. Uses {@code @WebMvcTest}
 * with the real {@link SecurityConfig}/{@link AuthFilter}, like
 * {@code ProjectControllerTest}. {@link RoleService} is mocked, so these
 * only verify the HTTP contract — the authorization rules themselves are
 * covered by {@code RoleServiceTest}/{@code RoleAuthorizationServiceTest}.
 */
@WebMvcTest(UserRoleController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class UserRoleControllerTest {

    private static final String ACCESS_TOKEN = "access-token-xyz";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoleService roleService;
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

    // ---- listRoles -----------------------------------------------------------

    @Test
    void listRolesReturnsRoles() throws Exception {
        UUID userId = UUID.randomUUID();
        when(roleService.listRoles(userId)).thenReturn(List.of(
                new UserRoleDTO(RoleLevel.PROJECT, UUID.randomUUID(), "Grid Expansion", "EDITOR",
                        Set.of("EDIT_PROJECT", "VIEW_PROJECT"))));

        mockMvc.perform(get("/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].level").value("PROJECT"))
                .andExpect(jsonPath("$.data[0].roleName").value("EDITOR"));
    }

    @Test
    void listRolesRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(get("/users/" + UUID.randomUUID() + "/roles"))
                .andExpect(status().isUnauthorized());

        verify(roleService, never()).listRoles(any());
    }

    @Test
    void listRolesPropagatesForbiddenAs403() throws Exception {
        UUID userId = UUID.randomUUID();
        when(roleService.listRoles(userId))
                .thenThrow(new org.enerscope.common.ForbiddenException("You are not allowed to view this user's roles"));

        mockMvc.perform(get("/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden());
    }

    // ---- assignRole ------------------------------------------------------

    @Test
    void assignRoleReturnsCreated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoleAssignmentRequestDTO(RoleLevel.PROJECT, projectId, "EDITOR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Role assigned"));

        verify(roleService).assignRole(eq(userId), any(RoleAssignmentRequestDTO.class));
    }

    @Test
    void assignRoleRejectsUnknownRoleTypeWith400() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Unknown role type: SUPERUSER"))
                .when(roleService).assignRole(eq(userId), any(RoleAssignmentRequestDTO.class));

        mockMvc.perform(post("/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoleAssignmentRequestDTO(RoleLevel.PLATFORM, null, "SUPERUSER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown role type: SUPERUSER"));
    }

    @Test
    void assignRoleRejectsInvalidBodyWithValidationError() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(roleService, never()).assignRole(any(), any());
    }

    @Test
    void assignRoleRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(post("/users/" + UUID.randomUUID() + "/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoleAssignmentRequestDTO(RoleLevel.PROJECT, UUID.randomUUID(), "EDITOR"))))
                .andExpect(status().isUnauthorized());

        verify(roleService, never()).assignRole(any(), any());
    }

    // ---- removeRole --------------------------------------------------------

    @Test
    void removeRoleReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(delete("/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoleAssignmentRequestDTO(RoleLevel.ORGANIZATION, orgId, "MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Role removed"));

        verify(roleService).removeRole(eq(userId), any(RoleAssignmentRequestDTO.class));
    }

    @Test
    void removeRolePropagatesForbiddenAs403() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new org.enerscope.common.ForbiddenException("You are not allowed to assign roles at this level"))
                .when(roleService).removeRole(eq(userId), any(RoleAssignmentRequestDTO.class));

        mockMvc.perform(delete("/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoleAssignmentRequestDTO(RoleLevel.PROJECT, UUID.randomUUID(), "ADMIN"))))
                .andExpect(status().isForbidden());
    }
}
