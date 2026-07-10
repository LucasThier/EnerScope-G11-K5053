package org.enerscope.user.service;

import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.logging.AppLogger;
import org.enerscope.user.model.User;
import org.enerscope.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        RegisterRequestDTO dto = new RegisterRequestDTO("New@Enerscope.org", "New", "User", "password123");
        when(userRepository.existsByMailIgnoreCase("New@Enerscope.org")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.register(dto);

        assertEquals("new@enerscope.org", saved.getMail());
        assertEquals("hashed", saved.getPasswordHash());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateMail() {
        RegisterRequestDTO dto = new RegisterRequestDTO("dup@enerscope.org", "Dup", "User", "password123");
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
}
