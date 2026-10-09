package org.enerscope.user.service;

import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.ForbiddenException;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.repository.OrganizationRepository;
import org.enerscope.logging.AppLogger;
import org.enerscope.user.dto.UpdateProfileRequestDTO;
import org.enerscope.user.dto.UserDetailDTO;
import org.enerscope.user.dto.UserListItemDTO;
import org.enerscope.user.dto.UserSearchResultDTO;
import org.enerscope.util.AuthUtil;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder encoder;
    private final AppLogger logger;

    public UserService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       PasswordEncoder encoder,
                       AppLogger logger) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
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

    @Transactional(readOnly = true)
    public List<UserListItemDTO> listAll() {
        AuthUtil.requirePlatformAdmin(logger, "list platform users");
        return userRepository.findListItems();
    }

    @Transactional(readOnly = true)
    public UserDetailDTO getDetail(UUID userId) {
        AuthUtil.requirePlatformAdmin(logger, "view user detail");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return new UserDetailDTO(
                user.getId(),
                user.getMail(),
                user.getFirstName(),
                user.getLastName(),
                user.getJobTitle(),
                user.getPlatformRole(),
                user.isActive(),
                userRepository.findOrganizationMembershipsForUser(userId),
                userRepository.findProjectMembershipsForUser(userId));
    }

    @Transactional(readOnly = true)
    public UserSearchResultDTO searchByMail(String mail) {
        User caller = AuthUtil.requireSession().getUser();
        boolean platformAdmin = AuthUtil.isPlatformAdmin(caller);
        if (!platformAdmin && !organizationRepository.ownsAnyActiveOrganization(
                caller.getId(), OrganizationMemberPermission.MANAGE_ORGANIZATION)) {
            logger.warn("User {} is not allowed to look users up by mail", caller.getMail());
            throw new ForbiddenException("You are not allowed to look users up");
        }

        return userRepository.findSearchResultByMail(mail)
                .orElseThrow(() -> {
                    if (!platformAdmin) {
                        logger.warn("User {} looked up an address with no active account",
                                caller.getMail());
                    }
                    return new EntityNotFoundException("User not found");
                });
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

    public void reactivateUser(UUID userId) {
        AuthUtil.requirePlatformAdmin(logger, "reactivate users");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.isActive()) {
            logger.debug("User {} is already active", user.getMail());
            return;
        }

        user.activate();
        userRepository.save(user);
        logger.info("Reactivated user {}", user.getMail());
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
