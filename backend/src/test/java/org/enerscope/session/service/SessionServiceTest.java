package org.enerscope.session.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.enerscope.jwt.JwtService;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final String SECRET = "test-secret-that-is-definitely-long-enough-256-bits-yes!!";

    @Mock
    private UserRepository userRepository;
    @Mock
    private AppLogger logger;

    private JwtService jwtService;
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 60, 30);
        sessionService = new SessionService(jwtService, userRepository, logger);
    }

    private static User storedUser(PlatformRole role) {
        return User.fromJwtClaims(UUID.randomUUID(), "stored@enerscope.org", "Stored", "User", role);
    }

    private String tokenFor(User user, PlatformRole role) {
        return jwtService.generateAccessToken(User.fromJwtClaims(user.getId(), user.getMail(),
                user.getFirstName(), user.getLastName(), role));
    }

    @Test
    void validateReturnsSessionWithStoredUserForActiveAccount() {
        User stored = storedUser(PlatformRole.USER);
        when(userRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        String token = tokenFor(stored, PlatformRole.USER);

        Optional<Session> session = sessionService.validate(token);

        assertTrue(session.isPresent());
        assertSame(stored, session.get().getUser());
        assertEquals(token, session.get().getToken());
    }

    @Test
    void validateRejectsTokenOfDeactivatedAccount() {
        User stored = storedUser(PlatformRole.USER);
        String token = tokenFor(stored, PlatformRole.USER);
        stored.deactivate();
        when(userRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        assertTrue(sessionService.validate(token).isEmpty());
        verify(logger).warn(anyString(), any(Object[].class));
    }

    @Test
    void validateRejectsTokenOfUnknownUser() {
        User ghost = storedUser(PlatformRole.ADMIN);
        when(userRepository.findById(ghost.getId())).thenReturn(Optional.empty());

        assertTrue(sessionService.validate(tokenFor(ghost, PlatformRole.ADMIN)).isEmpty());
    }

    @Test
    void validateUsesStoredRoleWhenAdminWasDemoted() {
        User stored = storedUser(PlatformRole.USER);
        when(userRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        Optional<Session> session = sessionService.validate(tokenFor(stored, PlatformRole.ADMIN));

        assertTrue(session.isPresent());
        assertEquals(PlatformRole.USER, session.get().getUser().getPlatformRole());
    }

    @Test
    void validateUsesStoredRoleWhenUserWasPromoted() {
        User stored = storedUser(PlatformRole.ADMIN);
        when(userRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        Optional<Session> session = sessionService.validate(tokenFor(stored, PlatformRole.USER));

        assertTrue(session.isPresent());
        assertEquals(PlatformRole.ADMIN, session.get().getUser().getPlatformRole());
    }

    @Test
    void validateAcceptsEarlierTokenAgainAfterReactivation() {
        User stored = storedUser(PlatformRole.USER);
        String token = tokenFor(stored, PlatformRole.USER);
        when(userRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        stored.deactivate();
        assertTrue(sessionService.validate(token).isEmpty());
        stored.activate();
        assertTrue(sessionService.validate(token).isPresent());
    }

    @Test
    void validateRejectsInvalidTokenWithoutLookingUpUsers() {
        assertTrue(sessionService.validate("not-a-jwt").isEmpty());
        assertTrue(sessionService.validate(null).isEmpty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void validateRejectsRefreshTokenUsedAsAccessToken() {
        User stored = storedUser(PlatformRole.USER);

        assertTrue(sessionService.validate(jwtService.generateRefreshToken(stored)).isEmpty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void validateRejectsAccessTokenWithMalformedSubject() {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject("not-a-uuid")
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertTrue(sessionService.validate(token).isEmpty());
        verifyNoInteractions(userRepository);
    }
}
