package org.enerscope.user.service;

import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.logging.AppLogger;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
