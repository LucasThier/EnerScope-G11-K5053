package org.enerscope.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.ForbiddenException;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.user.dto.ChangePasswordRequestDTO;
import org.enerscope.user.dto.UpdateProfileRequestDTO;
import org.enerscope.user.dto.UpdateRoleRequestDTO;
import org.enerscope.user.dto.UserDetailDTO;
import org.enerscope.user.dto.UserListItemDTO;
import org.enerscope.user.dto.UserOrganizationMembershipDTO;
import org.enerscope.user.dto.UserSearchResultDTO;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.model.User;
import org.enerscope.user.service.UserService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint tests for {@link UserController}, through the real
 * {@link SecurityConfig}/{@link AuthFilter} chain like the other controller
 * tests, so the unauthenticated case is refused by the actual filter rather
 * than by a stub. {@link UserService} is mocked.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class UserControllerTest {

    private static final String ACCESS_TOKEN = "access-token-xyz";

    private final UUID callerId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private SessionService sessionService;
    // GlobalExceptionHandler (@ControllerAdvice) needs an AppLogger bean to load.
    @MockitoBean
    private AppLogger logger;

    @BeforeEach
    void setUp() {
        User caller = User.fromJwtClaims(callerId, "jane@enerscope.org", "Jane", "Doe");
        Session session = new Session(ACCESS_TOKEN, caller, Instant.now().plusSeconds(3600));
        when(sessionService.validate(ACCESS_TOKEN)).thenReturn(Optional.of(session));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void listUsersReturnsTheProjection() throws Exception {
        when(userService.listAll()).thenReturn(List.of(new UserListItemDTO(
                UUID.randomUUID(), "jane@enerscope.org", "Jane", "Doe", "Analyst",
                PlatformRole.USER, true, 2L)));

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Users"))
                .andExpect(jsonPath("$.data[0].mail").value("jane@enerscope.org"))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[0].organizationCount").value(2));
    }

    @Test
    void listUsersPropagatesForbiddenWith403() throws Exception {
        when(userService.listAll())
                .thenThrow(new ForbiddenException("Only platform admins can list platform users"));

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsersRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).listAll();
    }

    @Test
    void getUserDetailReturnsTheFullDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        UserDetailDTO detail = new UserDetailDTO(
                userId, "jane@enerscope.org", "Jane", "Doe", "Analyst", PlatformRole.USER, true,
                List.of(new UserOrganizationMembershipDTO(
                        UUID.randomUUID(), "Acme", true, OrganizationMemberType.OWNER)),
                List.of());
        when(userService.getDetail(userId)).thenReturn(detail);

        mockMvc.perform(get("/users/" + userId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mail").value("jane@enerscope.org"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.organizations[0].organizationName").value("Acme"))
                .andExpect(jsonPath("$.data.projects").isEmpty());
    }

    @Test
    void getUserDetailAnswers404WithAFixedMessage() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getDetail(userId)).thenThrow(new EntityNotFoundException("User not found"));

        mockMvc.perform(get("/users/" + userId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void getUserDetailPropagatesForbiddenWith403() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getDetail(userId))
                .thenThrow(new ForbiddenException("Only platform admins can view user detail"));

        mockMvc.perform(get("/users/" + userId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserDetailRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(get("/users/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).getDetail(any());
    }

    @Test
    void updateRoleReturnsTheUpdatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        User promoted = new User("jane@enerscope.org", "Jane", "Doe", "hash", PlatformRole.ADMIN);
        when(userService.updateRole(userId, PlatformRole.ADMIN)).thenReturn(promoted);

        mockMvc.perform(patch("/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateRoleRequestDTO(PlatformRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Platform role updated"))
                .andExpect(jsonPath("$.data.platformRole").value("ADMIN"));
    }

    @Test
    void updateRoleRejectsANullRoleWithValidationError() throws Exception {
        mockMvc.perform(patch("/users/" + UUID.randomUUID() + "/role")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(userService, never()).updateRole(any(), any());
    }

    @Test
    void updateRolePropagatesTheLastAdminRefusalWith400() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.updateRole(userId, PlatformRole.USER))
                .thenThrow(new IllegalArgumentException(
                        "The platform would be left without an active administrator"));

        mockMvc.perform(patch("/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateRoleRequestDTO(PlatformRole.USER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("The platform would be left without an active administrator"));
    }

    @Test
    void updateRolePropagatesForbiddenWith403() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.updateRole(userId, PlatformRole.ADMIN))
                .thenThrow(new ForbiddenException("Only platform admins can change platform roles"));

        mockMvc.perform(patch("/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateRoleRequestDTO(PlatformRole.ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRoleRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(patch("/users/" + UUID.randomUUID() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateRoleRequestDTO(PlatformRole.ADMIN))))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).updateRole(any(), any());
    }

    @Test
    void reactivateUserReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/users/" + userId + "/reactivate")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User reactivated"));

        verify(userService).reactivateUser(userId);
    }

    @Test
    void reactivateUserRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(post("/users/" + UUID.randomUUID() + "/reactivate"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).reactivateUser(any());
    }

    @Test
    void deleteUserReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/users/" + userId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User deleted"));

        verify(userService).deactivateUser(userId);
    }

    @Test
    void deleteUserPropagatesTheLastAdminRefusalWith400() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("The platform would be left without an active administrator"))
                .when(userService).deactivateUser(userId);

        mockMvc.perform(delete("/users/" + userId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("The platform would be left without an active administrator"));
    }

    @Test
    void deleteUserRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(delete("/users/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).deactivateUser(any());
    }

    @Test
    void updateOwnProfileUsesTheCallerFromTheSession() throws Exception {
        User updated = new User("jane@enerscope.org", "Juana", "Perez", "hash",
                PlatformRole.USER, "Senior Analyst");
        when(userService.updateProfile(eq(callerId), any(UpdateProfileRequestDTO.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProfileRequestDTO("Juana", "Perez", "Senior Analyst"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Profile updated"))
                .andExpect(jsonPath("$.data.firstName").value("Juana"))
                .andExpect(jsonPath("$.data.jobTitle").value("Senior Analyst"));

        verify(userService).updateProfile(eq(callerId), any(UpdateProfileRequestDTO.class));
    }

    @Test
    void updateOwnProfileAcceptsABodyWithOnlyOneField() throws Exception {
        User updated = new User("jane@enerscope.org", "Juana", "Doe", "hash",
                PlatformRole.USER, "Analyst");
        when(userService.updateProfile(eq(callerId), any(UpdateProfileRequestDTO.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProfileRequestDTO("Juana", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastName").value("Doe"));
    }

    @Test
    void updateOwnProfileRejectsATooShortNameWithValidationError() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProfileRequestDTO("J", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(userService, never()).updateProfile(any(), any());
    }

    @Test
    void updateOwnProfilePropagatesAnEmptyPatchWith400() throws Exception {
        when(userService.updateProfile(eq(callerId), any(UpdateProfileRequestDTO.class)))
                .thenThrow(new IllegalArgumentException(
                        "At least one of firstName, lastName or jobTitle must be provided"));

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProfileRequestDTO(null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("At least one of firstName, lastName or jobTitle must be provided"));
    }

    @Test
    void updateOwnProfileRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProfileRequestDTO("Juana", null, null))))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).updateProfile(any(), any());
    }

    @Test
    void changeOwnPasswordUsesTheCallerFromTheSession() throws Exception {
        mockMvc.perform(patch("/users/me/password")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangePasswordRequestDTO("current-password", "new-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password changed"));

        // The account comes from the token, never from the body: this endpoint
        // cannot be pointed at somebody else's user id.
        verify(userService).changePassword(callerId, "current-password", "new-password");
    }

    @Test
    void changeOwnPasswordRejectsWrongCurrentPasswordWith400() throws Exception {
        doThrow(new IllegalArgumentException("Current password is incorrect"))
                .when(userService).changePassword(eq(callerId), any(), any());

        mockMvc.perform(patch("/users/me/password")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangePasswordRequestDTO("not-my-password", "new-password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    void changeOwnPasswordRejectsShortNewPasswordWithValidationError() throws Exception {
        mockMvc.perform(patch("/users/me/password")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangePasswordRequestDTO("current-password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.data.newPassword").value("Password must be at least 8 characters long"));

        verify(userService, never()).changePassword(any(), any(), any());
    }

    @Test
    void changeOwnPasswordRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(patch("/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangePasswordRequestDTO("current-password", "new-password"))))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).changePassword(any(), any(), any());
    }
    @Test
    void searchUserReturnsTheMinimalProjection() throws Exception {
        when(userService.searchByMail("jane@enerscope.org")).thenReturn(new UserSearchResultDTO(
                UUID.randomUUID(), "Jane", "Doe", "jane@enerscope.org"));

        mockMvc.perform(get("/users/search")
                        .param("mail", "jane@enerscope.org")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mail").value("jane@enerscope.org"))
                .andExpect(jsonPath("$.data.firstName").value("Jane"))
                .andExpect(jsonPath("$.data.platformRole").doesNotExist())
                .andExpect(jsonPath("$.data.active").doesNotExist());
    }

    @Test
    void searchUserAnswers404WithAFixedMessage() throws Exception {
        when(userService.searchByMail(any())).thenThrow(new EntityNotFoundException("User not found"));

        mockMvc.perform(get("/users/search")
                        .param("mail", "ghost@enerscope.org")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void searchUserPropagatesForbiddenWith403() throws Exception {
        when(userService.searchByMail(any()))
                .thenThrow(new ForbiddenException("You are not allowed to look users up"));

        mockMvc.perform(get("/users/search")
                        .param("mail", "jane@enerscope.org")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchUserRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(get("/users/search").param("mail", "jane@enerscope.org"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).searchByMail(any());
    }

}
