package org.enerscope.jwt;

import io.jsonwebtoken.Claims;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "test-secret-that-is-definitely-long-enough-256-bits-yes!!",
                60,
                30
        );
        user = User.fromJwtClaims(UUID.randomUUID(), "user@enerscope.org", "Jane", "Doe");
    }

    @Test
    void accessTokenCarriesUserClaims() {
        String token = jwtService.generateAccessToken(user);

        Optional<Claims> claims = jwtService.validateAccessToken(token);
        assertTrue(claims.isPresent());
        assertEquals(user.getId().toString(), claims.get().getSubject());
        assertEquals("user@enerscope.org", claims.get().get("mail", String.class));
        assertEquals("Jane", claims.get().get("firstName", String.class));
        assertEquals("Doe", claims.get().get("lastName", String.class));
        assertEquals("USER", claims.get().get("role", String.class));
    }

    @Test
    void accessTokenCarriesAdminRoleClaim() {
        User admin = User.fromJwtClaims(UUID.randomUUID(), "admin@enerscope.org", "Admin", "User", PlatformRole.ADMIN);

        String token = jwtService.generateAccessToken(admin);

        Optional<Claims> claims = jwtService.validateAccessToken(token);
        assertTrue(claims.isPresent());
        assertEquals("ADMIN", claims.get().get("role", String.class));
    }

    @Test
    void accessAndRefreshTokensAreNotInterchangeable() {
        String access = jwtService.generateAccessToken(user);
        String refresh = jwtService.generateRefreshToken(user);

        assertTrue(jwtService.validateAccessToken(access).isPresent());
        assertFalse(jwtService.validateRefreshToken(access).isPresent());

        assertTrue(jwtService.validateRefreshToken(refresh).isPresent());
        assertFalse(jwtService.validateAccessToken(refresh).isPresent());
    }

    @Test
    void rejectsGarbageAndBlankTokens() {
        assertFalse(jwtService.validateAccessToken("not-a-jwt").isPresent());
        assertFalse(jwtService.validateAccessToken("").isPresent());
        assertFalse(jwtService.validateAccessToken(null).isPresent());
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService other = new JwtService(
                "a-completely-different-secret-key-also-256-bits-long!!!",
                60,
                30
        );
        String foreignToken = other.generateAccessToken(user);
        assertFalse(jwtService.validateAccessToken(foreignToken).isPresent());
    }
}
