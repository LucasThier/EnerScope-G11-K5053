package org.enerscope.user.service;

import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.user.dto.UpdateProfileRequestDTO;
import org.enerscope.user.dto.UpdateRoleRequestDTO;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private AppLogger logger;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, encoder, logger);
    }

    @Test
    void registerHashesPasswordAndPersists() {
        RegisterRequestDTO dto = new RegisterRequestDTO("New@Enerscope.org", "New", "User", "password123", null, null);
        when(userRepository.existsByMailIgnoreCase("New@Enerscope.org")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.register(dto);

        assertEquals("new@enerscope.org", saved.getMail());
        assertEquals("hashed", saved.getPasswordHash());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerDefaultsToUserRoleWhenRoleOmitted() {
        RegisterRequestDTO dto = new RegisterRequestDTO("new@enerscope.org", "New", "User", "password123", null, null);
        when(userRepository.existsByMailIgnoreCase("new@enerscope.org")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.register(dto);

        assertEquals(PlatformRole.USER, saved.getPlatformRole());
    }

    @Test
    void registerPersistsJobTitle() {
        RegisterRequestDTO dto = new RegisterRequestDTO(
                "analyst@enerscope.org", "Maria", "Paz", "password123", null, "Senior Investment Analyst");
        when(userRepository.existsByMailIgnoreCase("analyst@enerscope.org")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("Senior Investment Analyst", userService.register(dto).getJobTitle());
    }

    @Test
    void registerLeavesJobTitleNullWhenOmitted() {
        RegisterRequestDTO dto = new RegisterRequestDTO(
                "plain@enerscope.org", "Plain", "User", "password123", null, null);
        when(userRepository.existsByMailIgnoreCase("plain@enerscope.org")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertNull(userService.register(dto).getJobTitle());
    }

    @Test
    void registerHonorsExplicitAdminRole() {
        RegisterRequestDTO dto = new RegisterRequestDTO("boss@enerscope.org", "Boss", "User", "password123", PlatformRole.ADMIN, null);
        when(userRepository.existsByMailIgnoreCase("boss@enerscope.org")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.register(dto);

        assertEquals(PlatformRole.ADMIN, saved.getPlatformRole());
    }

    @Test
    void registerRejectsDuplicateMail() {
        RegisterRequestDTO dto = new RegisterRequestDTO("dup@enerscope.org", "Dup", "User", "password123", null, null);
        when(userRepository.existsByMailIgnoreCase("dup@enerscope.org")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> userService.register(dto));
        verify(userRepository, never()).save(any());
        verify(encoder, never()).encode(anyString());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User caller) {
        Session session = new Session("token", caller, Instant.now().plusSeconds(3600));
        var auth = new UsernamePasswordAuthenticationToken(caller, null, List.of());
        auth.setDetails(session);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private User platformAdmin() {
        return new User("admin@enerscope.org", "Admin", "User", "hashed", PlatformRole.ADMIN);
    }

    private User regularUser() {
        return new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER);
    }

    @Test
    void updateRolePromotesAUserToAdminWithoutCountingAdmins() {
        UUID userId = UUID.randomUUID();
        User target = regularUser();
        authenticateAs(platformAdmin());
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.updateRole(userId, PlatformRole.ADMIN);

        assertEquals(PlatformRole.ADMIN, saved.getPlatformRole());
        verify(userRepository, never()).countByPlatformRoleAndActiveTrue(any());
    }

    @Test
    void updateRoleDemotesAnAdminWhenAnotherActiveAdminRemains() {
        UUID userId = UUID.randomUUID();
        User target = platformAdmin();
        authenticateAs(platformAdmin());
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));
        when(userRepository.countByPlatformRoleAndActiveTrue(PlatformRole.ADMIN)).thenReturn(2L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.updateRole(userId, PlatformRole.USER);

        assertEquals(PlatformRole.USER, saved.getPlatformRole());
    }

    @Test
    void updateRoleRefusesToDemoteTheLastActiveAdmin() {
        UUID userId = UUID.randomUUID();
        User target = platformAdmin();
        authenticateAs(platformAdmin());
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));
        when(userRepository.countByPlatformRoleAndActiveTrue(PlatformRole.ADMIN)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateRole(userId, PlatformRole.USER));
        assertEquals(PlatformRole.ADMIN, target.getPlatformRole());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRoleDemotesAnInactiveAdminWithoutCountingAdmins() {
        UUID userId = UUID.randomUUID();
        User target = platformAdmin();
        target.deactivate();
        authenticateAs(platformAdmin());
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(PlatformRole.USER, userService.updateRole(userId, PlatformRole.USER).getPlatformRole());
        verify(userRepository, never()).countByPlatformRoleAndActiveTrue(any());
    }

    @Test
    void updateRoleIsANoOpWhenTheRoleIsUnchanged() {
        UUID userId = UUID.randomUUID();
        User target = platformAdmin();
        authenticateAs(platformAdmin());
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        assertEquals(PlatformRole.ADMIN, userService.updateRole(userId, PlatformRole.ADMIN).getPlatformRole());
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).countByPlatformRoleAndActiveTrue(any());
    }

    @Test
    void updateRoleRejectsANullRole() {
        authenticateAs(platformAdmin());

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateRole(UUID.randomUUID(), null));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateRoleRejectsUnknownUser() {
        UUID userId = UUID.randomUUID();
        authenticateAs(platformAdmin());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateRole(userId, PlatformRole.ADMIN));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRoleRejectsANonAdminCallerWith403() {
        authenticateAs(regularUser());

        assertThrows(ForbiddenException.class,
                () -> userService.updateRole(UUID.randomUUID(), PlatformRole.ADMIN));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateRoleRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class,
                () -> userService.updateRole(UUID.randomUUID(), PlatformRole.ADMIN));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void deactivateUserDeactivatesARegularAccountWithoutCountingAdmins() {
        UUID userId = UUID.randomUUID();
        User target = regularUser();
        authenticateAs(platformAdmin());
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        userService.deactivateUser(userId);

        assertFalse(target.isActive());
        verify(userRepository).save(target);
        verify(userRepository, never()).countByPlatformRoleAndActiveTrue(any());
    }

    @Test
    void deactivateUserDeactivatesAnAdminWhenAnotherActiveAdminRemains() {
        UUID userId = UUID.randomUUID();
        User target = platformAdmin();
        authenticateAs(platformAdmin());
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));
        when(userRepository.countByPlatformRoleAndActiveTrue(PlatformRole.ADMIN)).thenReturn(2L);

        userService.deactivateUser(userId);

        assertFalse(target.isActive());
    }

    @Test
    void deactivateUserRefusesToDeactivateTheLastActiveAdmin() {
        UUID userId = UUID.randomUUID();
        User target = platformAdmin();
        authenticateAs(platformAdmin());
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));
        when(userRepository.countByPlatformRoleAndActiveTrue(PlatformRole.ADMIN)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class, () -> userService.deactivateUser(userId));
        assertTrue(target.isActive());
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUserIsIdempotent() {
        UUID userId = UUID.randomUUID();
        User target = regularUser();
        target.deactivate();
        authenticateAs(platformAdmin());
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        userService.deactivateUser(userId);

        assertFalse(target.isActive());
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUserRejectsUnknownUser() {
        UUID userId = UUID.randomUUID();
        authenticateAs(platformAdmin());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.deactivateUser(userId));
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUserRejectsANonAdminCallerWith403() {
        authenticateAs(regularUser());

        assertThrows(ForbiddenException.class, () -> userService.deactivateUser(UUID.randomUUID()));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void deactivateUserRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class, () -> userService.deactivateUser(UUID.randomUUID()));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void loginReturnsUserWhenPasswordMatches() {
        User user = new User("user@enerscope.org", "Jane", "Doe", "hashed");
        when(userRepository.findByMailIgnoreCase("user@enerscope.org")).thenReturn(Optional.of(user));
        when(encoder.matches("password123", "hashed")).thenReturn(true);

        assertEquals(user, userService.login("user@enerscope.org", "password123"));
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User("user@enerscope.org", "Jane", "Doe", "hashed");
        when(userRepository.findByMailIgnoreCase("user@enerscope.org")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> userService.login("user@enerscope.org", "wrong"));
    }

    @Test
    void loginRejectsADeactivatedAccount() {
        User user = new User("user@enerscope.org", "Jane", "Doe", "hashed");
        user.deactivate();
        when(userRepository.findByMailIgnoreCase("user@enerscope.org")).thenReturn(Optional.of(user));
        when(encoder.matches("password123", "hashed")).thenReturn(true);

        assertThrows(ForbiddenException.class,
                () -> userService.login("user@enerscope.org", "password123"));
    }

    @Test
    void loginChecksThePasswordBeforeTheActiveFlag() {
        User user = new User("user@enerscope.org", "Jane", "Doe", "hashed");
        user.deactivate();
        when(userRepository.findByMailIgnoreCase("user@enerscope.org")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> userService.login("user@enerscope.org", "wrong"));
    }

    @Test
    void loginRejectsUnknownMail() {
        when(userRepository.findByMailIgnoreCase("ghost@enerscope.org")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userService.login("ghost@enerscope.org", "whatever"));
    }

    // ---- changePassword ------------------------------------------------------

    @Test
    void changePasswordReplacesTheStoredHash() {
        UUID userId = UUID.randomUUID();
        User user = new User("jane@enerscope.org", "Jane", "Doe", "old-hash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(encoder.matches("current-password", "old-hash")).thenReturn(true);
        when(encoder.encode("new-password")).thenReturn("new-hash");

        userService.changePassword(userId, "current-password", "new-password");

        assertEquals("new-hash", user.getPasswordHash());
        verify(userRepository).save(user);
    }

    @Test
    void updateProfileChangesAllThreeFields() {
        UUID userId = UUID.randomUUID();
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hash", PlatformRole.USER, "Analyst");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.updateProfile(
                userId, new UpdateProfileRequestDTO("Juana", "Perez", "Senior Analyst"));

        assertEquals("Juana", saved.getFirstName());
        assertEquals("Perez", saved.getLastName());
        assertEquals("Senior Analyst", saved.getJobTitle());
    }

    @Test
    void updateProfileLeavesOutTheFieldsThatAreNull() {
        UUID userId = UUID.randomUUID();
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hash", PlatformRole.USER, "Analyst");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.updateProfile(userId, new UpdateProfileRequestDTO("Juana", null, null));

        assertEquals("Juana", saved.getFirstName());
        assertEquals("Doe", saved.getLastName());
        assertEquals("Analyst", saved.getJobTitle());
    }

    @Test
    void updateProfileClearsTheJobTitleWhenItArrivesBlank() {
        UUID userId = UUID.randomUUID();
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hash", PlatformRole.USER, "Analyst");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.updateProfile(userId, new UpdateProfileRequestDTO(null, null, "   "));

        assertNull(saved.getJobTitle());
    }

    @Test
    void updateProfileDoesNotLetABlankNameThrough() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> userService.updateProfile(
                userId, new UpdateProfileRequestDTO("   ", null, null)));
        assertThrows(IllegalArgumentException.class, () -> userService.updateProfile(
                userId, new UpdateProfileRequestDTO(null, "   ", null)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileRejectsAPatchWithEveryFieldNull() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> userService.updateProfile(
                userId, new UpdateProfileRequestDTO(null, null, null)));
        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileRejectsANullBody() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> userService.updateProfile(userId, null));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateProfileRejectsUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.updateProfile(
                userId, new UpdateProfileRequestDTO("Juana", null, null)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileNeverTouchesMailRoleOrPassword() {
        UUID userId = UUID.randomUUID();
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hash", PlatformRole.ADMIN, "Analyst");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.updateProfile(
                userId, new UpdateProfileRequestDTO("Juana", "Perez", "Lead"));

        assertEquals("jane@enerscope.org", saved.getMail());
        assertEquals(PlatformRole.ADMIN, saved.getPlatformRole());
        assertEquals("hash", saved.getPasswordHash());
    }

    @Test
    void changePasswordRejectsWrongCurrentPasswordAndLeavesTheHashAlone() {
        UUID userId = UUID.randomUUID();
        User user = new User("jane@enerscope.org", "Jane", "Doe", "old-hash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(encoder.matches("not-my-password", "old-hash")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword(userId, "not-my-password", "new-password"));

        assertEquals("old-hash", user.getPasswordHash());
        verify(encoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordRejectsUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword(userId, "current-password", "new-password"));

        verify(userRepository, never()).save(any());
    }
}
