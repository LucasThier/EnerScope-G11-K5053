package org.enerscope.user.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.organization.dto.AddOrganizationMemberRequestDTO;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.organization.service.OrganizationService;
import org.enerscope.project.dto.AddProjectMemberRequestDTO;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.service.ProjectService;
import org.enerscope.session.model.Session;
import org.enerscope.user.dto.RoleLevel;
import org.enerscope.user.dto.UserRoleDTO;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
import org.enerscope.util.AuthUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Cross-cutting view of a user's roles, spanning platform, organization and
 * project levels — the levels themselves stay owned by
 * {@link org.enerscope.user.model.User#getPlatformRole()},
 * {@code OrganizationMember} and {@code ProjectMember} respectively; this
 * service only aggregates and gates access to that aggregate.
 */
@Service
public class UserRoleService {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final OrganizationService organizationService;
    private final ProjectService projectService;

    public UserRoleService(UserRepository userRepository,
            OrganizationMemberRepository organizationMemberRepository,
            ProjectMemberRepository projectMemberRepository,
            OrganizationService organizationService,
            ProjectService projectService) {
        this.userRepository = userRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.organizationService = organizationService;
        this.projectService = projectService;
    }

    /**
     * Every role a user holds, across all three levels. Allowed for a
     * platform ADMIN, or for the user looking up their own roles.
     */
    @Transactional(readOnly = true)
    public List<UserRoleDTO> listRoles(UUID userId) {
        Session session = AuthUtil.currentSession();
        if (session == null) {
            throw new UnauthorizedException("Authentication required");
        }
        User caller = session.getUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (caller.getPlatformRole() != PlatformRole.ADMIN && !Objects.equals(userId, caller.getId())) {
            throw new ForbiddenException("You are not allowed to view this user's roles");
        }

        List<UserRoleDTO> roles = new ArrayList<>();
        roles.add(new UserRoleDTO(RoleLevel.PLATFORM, null, null, target.getPlatformRole().name()));
        organizationMemberRepository.findByUserIdWithOrganization(userId).stream()
                .flatMap(this::toRoleDTOs)
                .forEach(roles::add);
        projectMemberRepository.findByUserIdWithProject(userId).stream()
                .flatMap(this::toRoleDTOs)
                .forEach(roles::add);

        return roles;
    }

    private Stream<UserRoleDTO> toRoleDTOs(OrganizationMember membership) {
        return membership.getRoles().stream()
                .map(role -> new UserRoleDTO(RoleLevel.ORGANIZATION, membership.getOrganization().getId(),
                        membership.getOrganization().getName(), role.getMemberType().name()));
    }

    private Stream<UserRoleDTO> toRoleDTOs(ProjectMember membership) {
        return membership.getRoles().stream()
                .map(role -> new UserRoleDTO(RoleLevel.PROJECT, membership.getProject().getId(),
                        membership.getProject().getName(), role.getMemberType().name()));
    }

    /**
     * Assigns a user's platform-wide role. Only a platform ADMIN may call
     * this — the highest level in the hierarchy, so only a peer at that same
     * level can grant or revoke it.
     */
    public User assignPlatformRole(UUID userId, PlatformRole role) {
        Session session = AuthUtil.currentSession();
        if (session == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (session.getUser().getPlatformRole() != PlatformRole.ADMIN) {
            throw new ForbiddenException("Only a platform admin can assign platform roles");
        }
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        target.updatePlatformRole(role);
        return userRepository.save(target);
    }

    /**
     * Assigns a role to a user at any level, in one place. Dispatches to the
     * level's own service so every existing rule (who may assign what,
     * duplicate memberships, unknown resources) is enforced exactly once —
     * this method only validates the level/resource shape and translates the
     * free-form {@code roleType} into that level's enum.
     */
    public void assignRole(UUID userId, RoleLevel level, UUID resourceId, String roleType) {
        switch (level) {
            case PLATFORM -> {
                if (resourceId != null) {
                    throw new IllegalArgumentException("A platform role takes no resourceId");
                }
                assignPlatformRole(userId, PlatformRole.valueOf(roleType));
            }
            case ORGANIZATION -> {
                requireResourceId(level, resourceId);
                organizationService.addMember(resourceId,
                        new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.valueOf(roleType)));
            }
            case PROJECT -> {
                requireResourceId(level, resourceId);
                projectService.addMember(resourceId,
                        new AddProjectMemberRequestDTO(userId, ProjectMemberType.valueOf(roleType)));
            }
        }
    }

    /**
     * Removes a user's role at any level except {@link RoleLevel#PLATFORM}:
     * every user must have exactly one platform role, so it can only be
     * reassigned (see {@link #assignRole}), never removed outright.
     */
    public void removeRole(UUID userId, RoleLevel level, UUID resourceId) {
        switch (level) {
            case PLATFORM -> throw new IllegalArgumentException(
                    "The platform role cannot be removed, only reassigned");
            case ORGANIZATION -> {
                requireResourceId(level, resourceId);
                organizationService.removeMember(resourceId, userId);
            }
            case PROJECT -> {
                requireResourceId(level, resourceId);
                projectService.removeMember(resourceId, userId);
            }
        }
    }

    private void requireResourceId(RoleLevel level, UUID resourceId) {
        if (resourceId == null) {
            throw new IllegalArgumentException(level + " roles require a resourceId");
        }
    }
}
