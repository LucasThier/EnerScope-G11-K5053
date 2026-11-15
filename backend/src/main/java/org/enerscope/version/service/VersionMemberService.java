package org.enerscope.version.service;

import lombok.AllArgsConstructor;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.project.service.ProjectAccessGuard;
import org.enerscope.user.model.User;
import org.enerscope.user.repository.UserRepository;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.version.dto.AddVersionMemberRoleRequestDTO;
import org.enerscope.version.dto.ChangeVersionMemberRoleRequestDTO;
import org.enerscope.version.dto.VersionMemberCandidateDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberType;
import org.enerscope.version.repository.VersionMemberRepository;
import org.enerscope.version.repository.VersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Version-level membership, mirroring {@code ProjectService}'s member
 * endpoints — with one difference: a {@link VersionMember} may hold several
 * {@link VersionMemberRole}s at once (e.g. EDITOR for two verticals plus
 * SIMULATOR), so roles are granted and revoked one at a time instead of
 * replaced wholesale the way {@code ProjectService.changeMemberRole} does.
 */
@Service
@AllArgsConstructor
public class VersionMemberService {

    private final VersionRepository versionRepository;
    private final VersionMemberRepository versionMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final VersionAccessGuard accessGuard;
    private final AppLogger logger;

    /**
     * Grants a user one role on a version. Restricted to the owning project's
     * ADMIN or a platform ADMIN — without the check, any authenticated user
     * could grant themselves access to any version.
     */
    @Transactional
    public VersionMember addRole(UUID versionId, AddVersionMemberRoleRequestDTO data) {
        accessGuard.assertCanManageVersionRoles(versionId);
        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));
        validateRoleShape(data);

        User user = userRepository.findById(data.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isActive()) {
            throw new IllegalArgumentException("User is not active");
        }

        UUID projectId = projectRepository.findIdByVersionId(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version is not attached to any project"));
        boolean projectMember = projectMemberRepository.findByProjectIdAndUserId(projectId, data.userId())
                .filter(ProjectAccessGuard::isActive)
                .isPresent();
        if (!projectMember) {
            throw new IllegalArgumentException("User is not a member of the version's project");
        }

        VersionMember member = versionMemberRepository.findByVersionIdAndUserId(versionId, data.userId())
                .orElseGet(() -> new VersionMember(user, version));

        boolean duplicate = member.getRoles().stream().anyMatch(role ->
                role.getMemberType() == data.memberType() && Objects.equals(role.getVertical(), data.vertical()));
        if (duplicate) {
            throw new IllegalArgumentException("User already has this role");
        }

        member.addRole(new VersionMemberRole(data.memberType().name(), data.memberType(),
                VersionMember.defaultPermissionsFor(data.memberType()), data.vertical()));
        VersionMember saved = versionMemberRepository.save(member);
        logger.info("Granted {} ({}) on version {} to {}",
                data.memberType(), data.vertical(), version.getName(), user.getMail());
        return saved;
    }

    private void validateRoleShape(AddVersionMemberRoleRequestDTO data) {
        validateRoleShape(data == null ? null : data.memberType(), data == null ? null : data.vertical());
    }

    private void validateRoleShape(VersionMemberType memberType, VerticalEnum vertical) {
        if (memberType == null) {
            throw new IllegalArgumentException("memberType cannot be null");
        }
        if (memberType == VersionMemberType.EDITOR && vertical == null) {
            throw new IllegalArgumentException("EDITOR roles require a vertical");
        }
        if (memberType != VersionMemberType.EDITOR && vertical != null) {
            throw new IllegalArgumentException(
                    memberType + " roles apply to the whole version, not one vertical");
        }
    }

    /**
     * Changes one role of a member to another type/vertical in a single
     * transaction, keeping the role's id and the membership (unlike revoking
     * and granting, which deletes the member when it was their last role). The
     * permissions are replaced by those of the new type. Same callers as
     * {@link #addRole}: the owning project's ADMIN or a platform ADMIN; a
     * version ADMIN, EDITOR or SIMULATOR gets 403. The guard runs first, then
     * 404 for an unknown version, member or role (a member or role of another
     * version is also 404). Turning the role into one the member already holds
     * is 400, like {@code addRole}; asking for the role's current shape is a
     * no-op.
     */
    @Transactional
    public VersionMember changeRole(UUID versionId, UUID memberId, UUID roleId,
                                    ChangeVersionMemberRoleRequestDTO data) {
        accessGuard.assertCanManageVersionRoles(versionId);
        if (!versionRepository.existsById(versionId)) {
            throw new VersionNotFoundException(versionId);
        }
        VersionMember member = versionMemberRepository.findByIdAndVersionId(memberId, versionId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found"));
        VersionMemberRole role = member.getRoles().stream()
                .filter(r -> r.getId().equals(roleId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Role not found"));
        validateRoleShape(data == null ? null : data.memberType(), data == null ? null : data.vertical());

        boolean unchanged = role.getMemberType() == data.memberType()
                && Objects.equals(role.getVertical(), data.vertical());
        if (unchanged) {
            logger.debug("Role {} of user {} on version {} already has that shape; nothing to change",
                    roleId, member.getUser().getMail(), versionId);
            return member;
        }
        boolean duplicate = member.getRoles().stream().anyMatch(other -> other != role
                && other.getMemberType() == data.memberType()
                && Objects.equals(other.getVertical(), data.vertical()));
        if (duplicate) {
            throw new IllegalArgumentException("User already has this role");
        }

        VersionMemberType before = role.getMemberType();
        role.changeTo(data.memberType(), data.vertical(), VersionMember.defaultPermissionsFor(data.memberType()));
        VersionMember saved = versionMemberRepository.save(member);
        logger.info("Changed role {} of user {} on version {} from {} to {} ({})",
                roleId, member.getUser().getMail(), versionId, before, data.memberType(), data.vertical());
        return saved;
    }

    /**
     * Every member of the version with their roles. Restricted to the callers
     * who can manage its roles: the owning project's ADMIN or a platform ADMIN.
     */
    @Transactional(readOnly = true)
    public List<VersionMember> listMembers(UUID versionId) {
        accessGuard.assertCanManageVersionRoles(versionId);
        if (!versionRepository.existsById(versionId)) {
            throw new VersionNotFoundException(versionId);
        }
        return versionMemberRepository.findByVersionIdWithUser(versionId);
    }

    /**
     * Revokes a single role. If it was the member's only role, the
     * membership row itself is deleted — there is no value in a
     * {@code VersionMember} with no roles, unlike a {@code ProjectMember},
     * which always needs at least one.
     */
    @Transactional
    public void removeRole(UUID versionId, UUID memberId, UUID roleId) {
        accessGuard.assertCanManageVersionRoles(versionId);
        if (!versionRepository.existsById(versionId)) {
            throw new VersionNotFoundException(versionId);
        }

        VersionMember member = versionMemberRepository.findByIdAndVersionId(memberId, versionId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found"));
        VersionMemberRole role = member.getRoles().stream()
                .filter(r -> r.getId().equals(roleId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Role not found"));

        member.removeRole(role);
        if (member.getRoles().isEmpty()) {
            versionMemberRepository.delete(member);
            logger.info("Removed user {} from version {}: their last role was revoked",
                    member.getUser().getMail(), versionId);
        } else {
            versionMemberRepository.save(member);
            logger.info("Revoked a role from user {} on version {}", member.getUser().getMail(), versionId);
        }
    }

    /**
     * Users who can be granted a version role: active members of the
     * version's owning project. A version role narrows what a project member
     * may do on the version rather than granting access to someone outside
     * the project, so only people who already hold project membership are
     * candidates.
     */
    @Transactional(readOnly = true)
    public List<VersionMemberCandidateDTO> listMemberCandidates(UUID versionId, String q) {
        accessGuard.assertCanManageVersionRoles(versionId);
        if (!versionRepository.existsById(versionId)) {
            throw new VersionNotFoundException(versionId);
        }
        UUID projectId = projectRepository.findIdByVersionId(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version is not attached to any project"));

        String needle = q == null ? null : q.trim().toLowerCase();
        return projectMemberRepository.findByProjectIdWithUser(projectId).stream()
                .filter(ProjectAccessGuard::isActive)
                .map(ProjectMember::getUser)
                .filter(User::isActive)
                .filter(user -> matches(user, needle))
                .map(user -> new VersionMemberCandidateDTO(
                        user.getId(), user.getFirstName(), user.getLastName(), user.getMail()))
                .distinct()
                .toList();
    }

    private boolean matches(User user, String needle) {
        if (needle == null || needle.isBlank()) {
            return true;
        }
        String haystack = (user.getFirstName() + " " + user.getLastName() + " " + user.getMail()).toLowerCase();
        return haystack.contains(needle);
    }
}
