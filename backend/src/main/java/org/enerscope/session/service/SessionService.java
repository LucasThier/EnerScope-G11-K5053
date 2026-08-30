package org.enerscope.session.service;

import io.jsonwebtoken.Claims;
import org.enerscope.jwt.JwtService;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private final JwtService jwtService;

    public SessionService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public Session create(User user) {
        String token = jwtService.generateAccessToken(user);
        Instant expiresAt = Instant.now().plus(jwtService.getAccessTokenExpiry());
        return new Session(token, user, expiresAt);
    }

    public Optional<Session> validate(String token) {
        return jwtService.validateAccessToken(token)
                .map(claims -> {
                    User user = buildUserFromClaims(claims);
                    Instant expiresAt = jwtService.getExpiry(claims);
                    return new Session(token, user, expiresAt);
                });
    }

    public String generateRefreshToken(User user) {
        return jwtService.generateRefreshToken(user);
    }

    public Optional<UUID> validateRefreshToken(String token) {
        return jwtService.validateRefreshToken(token)
                .map(claims -> UUID.fromString(claims.getSubject()));
    }

    private User buildUserFromClaims(Claims claims) {
        UUID id = UUID.fromString(claims.getSubject());
        String mail = claims.get("mail", String.class);
        String firstName = claims.get("firstName", String.class);
        String lastName = claims.get("lastName", String.class);
        PlatformRole role = parseRole(claims.get("role", String.class));
        return User.fromJwtClaims(id, mail, firstName, lastName, role);
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
