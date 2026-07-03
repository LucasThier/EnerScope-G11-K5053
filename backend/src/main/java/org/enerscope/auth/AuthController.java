package org.enerscope.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.enerscope.dto.session.LoginRequestDTO;
import org.enerscope.dto.session.NewSessionDTO;
import org.enerscope.dto.session.RefreshRequestDTO;
import org.enerscope.dto.session.RegisterRequestDTO;
import org.enerscope.session.Session;
import org.enerscope.session.SessionService;
import org.enerscope.user.User;
import org.enerscope.user.UserRepository;
import org.enerscope.user.UserService;
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
        Session session = sessionService.create(user);
        String refresh = sessionService.generateRefreshToken(user);

        return Responses.ok("Authenticated", new NewSessionDTO(
                session.getToken(),
                refresh,
                session.getExpiresAt()
        ));
    }

    @PostMapping("/register")
    @Operation(summary = "Create a new account and start a session")
    public ResponseEntity<ApiResponse<NewSessionDTO>> register(@Valid @RequestBody RegisterRequestDTO body) {
        User user = userService.register(body);
        Session session = sessionService.create(user);
        String refresh = sessionService.generateRefreshToken(user);

        return Responses.created("Registered", new NewSessionDTO(
                session.getToken(),
                refresh,
                session.getExpiresAt()
        ));
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

        Session newSession = sessionService.create(user);
        String newRefresh = sessionService.generateRefreshToken(user);

        return Responses.ok("Session renewed", new NewSessionDTO(
                newSession.getToken(),
                newRefresh,
                newSession.getExpiresAt()
        ));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out (client discards the stored tokens)")
    public ResponseEntity<ApiResponse<Void>> logout() {
        // JWT is stateless: the client removes the tokens from LocalStorage.
        return Responses.ok("Session closed");
    }
}
