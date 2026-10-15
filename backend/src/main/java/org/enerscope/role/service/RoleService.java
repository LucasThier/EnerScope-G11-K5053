package org.enerscope.role.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.organization.repository.OrganizationRepository;
import org.enerscope.organization.service.OrganizationService;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.project.service.ProjectService;
import org.enerscope.role.dto.RoleAssignmentRequestDTO;
import org.enerscope.role.dto.RoleLevel;
import org.enerscope.role.dto.UserRoleDTO;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
import org.enerscope.util.AuthUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final RoleAuthorizationService roleAuthorizationService;
    private final AppLogger logger;

    public RoleService(UserRepository userRepository,
                        OrganizationRepository organizationRepository,
                        OrganizationMemberRepository organizationMemberRepository,
                        ProjectRepository projectRepository,
                        ProjectMemberRepository projectMemberRepository,
                        RoleAuthorizationService roleAuthorizationService,
                        AppLogger logger) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.roleAuthorizationService = roleAuthorizationService;
        this.logger = logger;
    }

    /**
     * Every role a user holds across the three levels. Callable by the user
     * themself or a platform ADMIN — anyone else gets a 403, since this spans
     * resources the caller may have no relation to.
     */
    @Transactional(readOnly = true)
    public List<UserRoleDTO> listRoles(UUID targetUserId) {
        assertCanViewRoles(targetUserId);
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<UserRoleDTO> roles = new ArrayList<>();
        roles.add(new UserRoleDTO(RoleLevel.PLATFORM, null, null,
                target.getPlatformRole().name(), Set.of(target.getPlatformRole().name())));

        for (OrganizationMember member : organizationMemberRepository.findByUserId(targetUserId)) {
            for (OrganizationMemberRole role : member.getRoles()) {
                roles.add(new UserRoleDTO(
                        RoleLevel.ORGANIZATION,
                        member.getOrganization().getId(),
                        member.getOrganization().getName(),
                        role.getMemberType().name(),
                        permissionNames(role.getPermissions())));
            }
        }

        for (ProjectMember member : projectMemberRepository.findByUserId(targetUserId)) {
            for (ProjectMemberRole role : member.getRoles()) {
                roles.add(new UserRoleDTO(
                        RoleLevel.PROJECT,
                        member.getProject().getId(),
                        member.getProject().getName(),
                        role.getMemberType().name(),
                        permissionNames(role.getPermissions())));
            }
        }

        return roles;
    }

    @Transactional
    public void assignRole(UUID targetUserId, RoleAssignmentRequestDTO data) {
        roleAuthorizationService.assertCanAssign(data.level(), data.resourceId());
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        switch (data.level()) {
            case PLATFORM -> {
                target.updatePlatformRole(parseEnum(PlatformRole.class, data.roleType()));
                userRepository.save(target);
            }
            case ORGANIZATION -> assignOrganizationRole(target, data.resourceId(), data.roleType());
            case PROJECT -> assignProjectRole(target, data.resourceId(), data.roleType());
        }
        logger.info("Assigned {} role {} to user {} on resource {}",
                data.level(), data.roleType(), target.getMail(), data.resourceId());
    }

    @Transactional
    public void removeRole(UUID targetUserId, RoleAssignmentRequestDTO data) {
        roleAuthorizationService.assertCanAssign(data.level(), data.resourceId());
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        switch (data.level()) {
            case PLATFORM -> {
                target.updatePlatformRole(PlatformRole.USER);
                userRepository.save(target);
            }
            case ORGANIZATION -> removeOrganizationRole(target, data.resourceId(), data.roleType());
            case PROJECT -> removeProjectRole(target, data.resourceId(), data.roleType());
        }
        logger.info("Removed {} role {} from user {} on resource {}",
                data.level(), data.roleType(), target.getMail(), data.resourceId());
    }

    private void assignOrganizationRole(User target, UUID organizationId, String roleType) {
        OrganizationMemberType memberType = parseEnum(OrganizationMemberType.class, roleType);
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        OrganizationMember member = organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, target.getId())
                .orElseGet(() -> {
                    OrganizationMember created = new OrganizationMember(target, organization);
                    organization.addMember(created);
                    return created;
                });
        member.addRole(new OrganizationMemberRole(
                memberType.name(), memberType, OrganizationService.defaultPermissionsFor(memberType)));
        organizationMemberRepository.save(member);
    }

    private void assignProjectRole(User target, UUID projectId, String roleType) {
        ProjectMemberType memberType = parseEnum(ProjectMemberType.class, roleType);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserId(projectId, target.getId())
                .orElseGet(() -> {
                    ProjectMember created = new ProjectMember(target, project);
                    project.addMember(created);
                    return created;
                });
        member.addRole(new ProjectMemberRole(
                memberType.name(), memberType, ProjectService.defaultPermissionsFor(memberType)));
        projectMemberRepository.save(member);
    }

    private void removeOrganizationRole(User target, UUID organizationId, String roleType) {
        OrganizationMemberType memberType = parseEnum(OrganizationMemberType.class, roleType);
        OrganizationMember member = organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, target.getId())
                .orElseThrow(() -> new IllegalArgumentException("User has no roles in this organization"));

        member.getRoles().removeIf(role -> role.getMemberType() == memberType);
        if (member.getRoles().isEmpty()) {
            organizationMemberRepository.delete(member);
        } else {
            organizationMemberRepository.save(member);
        }
    }

    private void removeProjectRole(User target, UUID projectId, String roleType) {
        ProjectMemberType memberType = parseEnum(ProjectMemberType.class, roleType);
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserId(projectId, target.getId())
                .orElseThrow(() -> new IllegalArgumentException("User has no roles in this project"));

        member.getRoles().removeIf(role -> role.getMemberType() == memberType);
        if (member.getRoles().isEmpty()) {
            projectMemberRepository.delete(member);
        } else {
            projectMemberRepository.save(member);
        }
    }

    private void assertCanViewRoles(UUID targetUserId) {
        Session session = AuthUtil.currentSession();
        if (session == null) {
            throw new UnauthorizedException("Authentication required");
        }
        User caller = session.getUser();
        if (caller.getPlatformRole() == PlatformRole.ADMIN || targetUserId.equals(caller.getId())) {
            return;
        }
        throw new ForbiddenException("You are not allowed to view this user's roles");
    }

    private static <E extends Enum<E>> Set<String> permissionNames(Set<E> permissions) {
        return permissions.stream().map(Enum::name).collect(Collectors.toSet());
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown role type: " + value);
        }
    }
}
