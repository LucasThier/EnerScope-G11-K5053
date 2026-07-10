package org.enerscope.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.auth.dto.LoginRequestDTO;
import org.enerscope.auth.dto.RefreshRequestDTO;
import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.model.User;
import org.enerscope.user.repository.UserRepository;
import org.enerscope.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint tests for {@link AuthController}. Uses {@code @WebMvcTest} (web layer
 * only, no database) with the real {@link SecurityConfig}/{@link AuthFilter} so
 * the {@code /auth/**} routes are exercised through the actual filter chain.
 * The service and repository collaborators are mocked.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class AuthControllerTest {

    private static final String ACCESS_TOKEN = "access-token-xyz";
    private static final String REFRESH_TOKEN = "refresh-token-abc";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SessionService sessionService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private UserRepository userRepository;
    // GlobalExceptionHandler (@ControllerAdvice) needs an AppLogger bean to load.
    @MockitoBean
    private AppLogger logger;

    private User sampleUser() {
        return User.fromJwtClaims(UUID.randomUUID(), "jane@enerscope.org", "Jane", "Doe");
    }

    private Session sampleSession(User user) {
        return new Session(ACCESS_TOKEN, user, Instant.now().plusSeconds(3600));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ---- register --------------------------------------------------------

    @Test
    void registerCreatesAccountAndReturnsSession() throws Exception {
        User user = sampleUser();
        when(userService.register(any(RegisterRequestDTO.class))).thenReturn(user);
        when(sessionService.create(any(User.class))).thenReturn(sampleSession(user));
        when(sessionService.generateRefreshToken(any(User.class))).thenReturn(REFRESH_TOKEN);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequestDTO(
                                "jane@enerscope.org", "Jane", "Doe", "password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registered"))
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").value(REFRESH_TOKEN))
                .andExpect(jsonPath("$.data.expiresAt").exists());
    }

    @Test
    void registerRejectsDuplicateEmailWith400() throws Exception {
        when(userService.register(any(RegisterRequestDTO.class)))
                .thenThrow(new IllegalArgumentException("An account with that email already exists"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequestDTO(
                                "dup@enerscope.org", "Dup", "User", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("An account with that email already exists"));
    }

    @Test
    void registerRejectsInvalidBodyWithValidationError() throws Exception {
        String invalidBody = """
                {"mail":"not-an-email","firstName":"J","lastName":"Doe","password":"short"}
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.data").exists());

        verify(userService, never()).register(any());
    }

    // ---- login -----------------------------------------------------------

    @Test
    void loginReturnsSessionForValidCredentials() throws Exception {
        User user = sampleUser();
        when(userService.login(eq("jane@enerscope.org"), eq("password123"))).thenReturn(user);
        when(sessionService.create(any(User.class))).thenReturn(sampleSession(user));
        when(sessionService.generateRefreshToken(any(User.class))).thenReturn(REFRESH_TOKEN);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequestDTO("jane@enerscope.org", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Authenticated"))
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").value(REFRESH_TOKEN));
    }

    @Test
    void loginRejectsBadCredentialsWith400() throws Exception {
        when(userService.login(any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid email or password"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequestDTO("jane@enerscope.org", "wrong-password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void loginRejectsBlankFieldsWithValidationError() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mail\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(userService, never()).login(any(), any());
    }

    // ---- refresh ---------------------------------------------------------

    @Test
    void refreshIssuesNewSessionForValidToken() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = sampleUser();
        when(sessionService.validateRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(sessionService.create(any(User.class))).thenReturn(sampleSession(user));
        when(sessionService.generateRefreshToken(any(User.class))).thenReturn("new-refresh-token");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequestDTO(REFRESH_TOKEN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Session renewed"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    void refreshRejectsInvalidTokenWith401() throws Exception {
        when(sessionService.validateRefreshToken(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequestDTO("expired-or-garbage"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
    }

    @Test
    void refreshRejectsWhenUserNoLongerExistsWith401() throws Exception {
        UUID userId = UUID.randomUUID();
        when(sessionService.validateRefreshToken(any())).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequestDTO(REFRESH_TOKEN))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void refreshRejectsBlankTokenWithValidationError() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(sessionService, never()).validateRefreshToken(any());
    }

    // ---- logout ----------------------------------------------------------

    @Test
    void logoutReturnsOk() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Session closed"));
    }
}
