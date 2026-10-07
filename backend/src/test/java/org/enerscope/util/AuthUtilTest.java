package org.enerscope.util;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link AuthUtil}, which holds the session lookup and the
 * platform-ADMIN check every service runs. These rules used to be copy-pasted
 * across three services, so they are now tested once, here, on top of the
 * coverage each caller already has.
 */
@ExtendWith(MockitoExtension.class)
class AuthUtilTest {

    @Mock
    private AppLogger logger;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentSessionReturnsNullWhenThereIsNoAuthentication() {
        assertNull(AuthUtil.currentSession());
    }

    @Test
    void requireSessionReturnsTheSessionBoundToTheRequest() {
        Session session = authenticateAs(regularUser());

        assertSame(session, AuthUtil.requireSession());
    }

    @Test
    void requireSessionRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class, AuthUtil::requireSession);
    }

    @Test
    void isPlatformAdminIsTrueOnlyForAdmins() {
        assertTrue(AuthUtil.isPlatformAdmin(platformAdmin()));
        assertFalse(AuthUtil.isPlatformAdmin(regularUser()));
    }

    @Test
    void requirePlatformAdminAllowsAnAdminWithoutLogging() {
        authenticateAs(platformAdmin());

        assertDoesNotThrow(() -> AuthUtil.requirePlatformAdmin(logger, "create organizations"));

        verifyNoInteractions(logger);
    }

    @Test
    void requirePlatformAdminRejectsRegularUserWith403AndLogsTheRefusal() {
        authenticateAs(regularUser());

        ForbiddenException thrown = assertThrows(ForbiddenException.class,
                () -> AuthUtil.requirePlatformAdmin(logger, "create organizations"));

        assertEquals("Only platform admins can create organizations", thrown.getMessage());
        verify(logger).warn("User {} is not a platform admin and may not {}",
                "member@enerscope.org", "create organizations");
    }

    @Test
    void requirePlatformAdminRejectsUnauthenticatedBeforeLogging() {
        assertThrows(UnauthorizedException.class,
                () -> AuthUtil.requirePlatformAdmin(logger, "create organizations"));

        verifyNoInteractions(logger);
    }

    private User platformAdmin() {
        return User.fromJwtClaims(UUID.randomUUID(), "admin@enerscope.org", "Admin", "User", PlatformRole.ADMIN);
    }

    private User regularUser() {
        return User.fromJwtClaims(UUID.randomUUID(), "member@enerscope.org", "Jane", "Doe", PlatformRole.USER);
    }

    private Session authenticateAs(User caller) {
        Session session = new Session("token", caller, Instant.now().plusSeconds(3600));
        var auth = new UsernamePasswordAuthenticationToken(caller, null, List.of());
        auth.setDetails(session);
        SecurityContextHolder.getContext().setAuthentication(auth);
        return session;
    }
}
