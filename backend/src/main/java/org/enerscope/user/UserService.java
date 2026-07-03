package org.enerscope.user;

import org.enerscope.dto.session.RegisterRequestDTO;
import org.enerscope.logging.AppLogger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final AppLogger logger;

    public UserService(UserRepository userRepository, PasswordEncoder encoder, AppLogger logger) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.logger = logger;
    }

    public User register(RegisterRequestDTO data) {
        if (userRepository.existsByMailIgnoreCase(data.mail())) {
            throw new IllegalArgumentException("An account with that email already exists");
        }
        String hash = encoder.encode(data.password());
        User user = new User(data.mail(), data.firstName(), data.lastName(), hash);
        User saved = userRepository.save(user);
        logger.info("Registered new user {}", saved.getMail());
        return saved;
    }

    public User login(String mail, String rawPassword) {
        return userRepository.findByMailIgnoreCase(mail)
                .filter(u -> encoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.updatePasswordHash(encoder.encode(newPassword));
        userRepository.save(user);
        logger.info("Password changed for user {}", user.getMail());
    }
}
