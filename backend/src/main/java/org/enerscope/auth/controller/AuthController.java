package org.enerscope.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.enerscope.auth.dto.LoginRequestDTO;
import org.enerscope.auth.dto.NewSessionDTO;
import org.enerscope.auth.dto.RefreshRequestDTO;
import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.dto.UserSummaryDTO;
import org.enerscope.user.model.User;
import org.enerscope.user.repository.UserRepository;
import org.enerscope.user.service.UserService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Registration, login and token lifecycle")
public class AuthController {

    private final SessionService sessionService;
    private final UserService userService;
    private final UserRepository userRepository;

    public AuthController(SessionService sessionService,
                          UserService userService,
                          UserRepository userRepository) {
        this.sessionService = sessionService;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password")
    public ResponseEntity<ApiResponse<NewSessionDTO>> login(@Valid @RequestBody LoginRequestDTO body) {
        User user = userService.login(body.mail(), body.password());
        return Responses.ok("Authenticated", newSession(user));
    }

    @PostMapping("/register")
    @Operation(summary = "Create a new account (platform administrators only)",
            description = "Registration is not self-service: only an authenticated platform ADMIN "
                    + "can create accounts. The new user is created but no session is started for "
                    + "them; they log in afterwards with the credentials the admin shares.")
    public ResponseEntity<ApiResponse<UserSummaryDTO>> register(@Valid @RequestBody RegisterRequestDTO body) {
        User user = userService.register(body);
        return Responses.created("User registered", UserSummaryDTO.from(user));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new session")
    public ResponseEntity<ApiResponse<NewSessionDTO>> refresh(@Valid @RequestBody RefreshRequestDTO body) {
        UUID userId = sessionService.validateRefreshToken(body.refreshToken()).orElse(null);
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid or expired refresh token"));
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("User not found"));
        }

        return Responses.ok("Session renewed", newSession(user));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out (client discards the stored tokens)")
    public ResponseEntity<ApiResponse<Void>> logout() {
        // JWT is stateless: the client removes the tokens from LocalStorage.
        return Responses.ok("Session closed");
    }

    private NewSessionDTO newSession(User user) {
        Session session = sessionService.create(user);
        String refresh = sessionService.generateRefreshToken(user);
        return new NewSessionDTO(
                session.getToken(),
                refresh,
                session.getExpiresAt(),
                UserSummaryDTO.from(user));
    }
}
