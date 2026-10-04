package org.enerscope.user.service;

import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.common.ForbiddenException;
import org.enerscope.logging.AppLogger;
import org.enerscope.user.dto.UpdateProfileRequestDTO;
import org.enerscope.util.AuthUtil;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
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
        PlatformRole role = data.role() != null ? data.role() : PlatformRole.USER;
        String hash = encoder.encode(data.password());
        User user = new User(data.mail(), data.firstName(), data.lastName(), hash, role, data.jobTitle());
        User saved = userRepository.save(user);
        logger.info("Registered new user {} with role {}", saved.getMail(), role);
        return saved;
    }

    public User login(String mail, String rawPassword) {
        User user = userRepository.findByMailIgnoreCase(mail)
                .filter(u -> encoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (!user.isActive()) {
            logger.warn("Login refused for deactivated account {}", user.getMail());
            throw new ForbiddenException("This account has been deactivated");
        }
        return user;
    }

    public User updateProfile(UUID userId, UpdateProfileRequestDTO data) {
        if (data == null) {
            throw new IllegalArgumentException("data cannot be null");
        }
        if (data.firstName() == null && data.lastName() == null && data.jobTitle() == null) {
            throw new IllegalArgumentException(
                    "At least one of firstName, lastName or jobTitle must be provided");
        }
        if (data.firstName() != null && data.firstName().isBlank()) {
            throw new IllegalArgumentException("First name cannot be blank");
        }
        if (data.lastName() != null && data.lastName().isBlank()) {
            throw new IllegalArgumentException("Last name cannot be blank");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.updateProfile(data.firstName(), data.lastName(), data.jobTitle());
        User saved = userRepository.save(user);
        logger.info("Profile updated for user {}", saved.getMail());
        return saved;
    }

    public User updateRole(UUID userId, PlatformRole newRole) {
        AuthUtil.requirePlatformAdmin(logger, "change platform roles");
        if (newRole == null) {
            throw new IllegalArgumentException("platformRole cannot be null");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getPlatformRole() == newRole) {
            return user;
        }
        if (newRole == PlatformRole.USER) {
            assertWouldLeaveAnActiveAdmin(user, "demote");
        }

        user.updatePlatformRole(newRole);
        User saved = userRepository.save(user);
        logger.info("Changed the platform role of {} to {}", saved.getMail(), newRole);
        return saved;
    }

    public void deactivateUser(UUID userId) {
        AuthUtil.requirePlatformAdmin(logger, "deactivate users");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isActive()) {
            logger.debug("User {} is already inactive", user.getMail());
            return;
        }
        assertWouldLeaveAnActiveAdmin(user, "deactivate");

        user.deactivate();
        userRepository.save(user);
        logger.info("Deactivated user {}", user.getMail());
    }

    private void assertWouldLeaveAnActiveAdmin(User user, String action) {
        if (user.getPlatformRole() != PlatformRole.ADMIN || !user.isActive()) {
            return;
        }
        if (userRepository.countByPlatformRoleAndActiveTrue(PlatformRole.ADMIN) > 1) {
            return;
        }
        logger.warn("Refused to {} {}: the platform would be left with no active administrator",
                action, user.getMail());
        throw new IllegalArgumentException("The platform would be left without an active administrator");
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
