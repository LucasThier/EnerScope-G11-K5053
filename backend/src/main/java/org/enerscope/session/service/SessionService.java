package org.enerscope.session.service;

import io.jsonwebtoken.Claims;
import org.enerscope.jwt.JwtService;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AppLogger logger;

    public SessionService(JwtService jwtService, UserRepository userRepository, AppLogger logger) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.logger = logger;
    }

    public Session create(User user) {
        String token = jwtService.generateAccessToken(user);
        Instant expiresAt = Instant.now().plus(jwtService.getAccessTokenExpiry());
        return new Session(token, user, expiresAt);
    }

    public Optional<Session> validate(String token) {
        return jwtService.validateAccessToken(token)
                .flatMap(claims -> loadCurrentUser(claims)
                        .map(user -> new Session(token, user, jwtService.getExpiry(claims))));
    }

    public String generateRefreshToken(User user) {
        return jwtService.generateRefreshToken(user);
    }

    public Optional<UUID> validateRefreshToken(String token) {
        return jwtService.validateRefreshToken(token)
                .map(claims -> UUID.fromString(claims.getSubject()));
    }

    private Optional<User> loadCurrentUser(Claims claims) {
        UUID id;
        try {
            id = UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            logger.warn("Rejected access token with a malformed subject");
            return Optional.empty();
        }
        Optional<User> found = userRepository.findById(id);
        if (found.isEmpty()) {
            logger.warn("Rejected access token for unknown user {}", id);
            return Optional.empty();
        }
        User user = found.get();
        if (!user.isActive()) {
            logger.warn("Rejected access token for deactivated account {}", user.getMail());
            return Optional.empty();
        }
        PlatformRole tokenRole = parseRole(claims.get("role", String.class));
        if (tokenRole != user.getPlatformRole()) {
            logger.debug("Access token of {} carries role {} but the current role is {}",
                    user.getMail(), tokenRole, user.getPlatformRole());
        }
        return Optional.of(user);
    }

    // Tokens issued before the role claim existed (or with an unknown value)
    // are treated as regular users rather than failing the request.
    private PlatformRole parseRole(String rawRole) {
        if (rawRole == null) {
            return PlatformRole.USER;
        }
        try {
            return PlatformRole.valueOf(rawRole);
        } catch (IllegalArgumentException ex) {
            return PlatformRole.USER;
        }
    }
}
