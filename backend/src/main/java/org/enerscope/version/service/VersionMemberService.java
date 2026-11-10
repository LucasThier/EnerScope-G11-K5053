package org.enerscope.version.service;

import lombok.AllArgsConstructor;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.user.model.User;
import org.enerscope.user.repository.UserRepository;
import org.enerscope.version.dto.AddVersionMemberRoleRequestDTO;
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
     * Grants a user one role on a version. Restricted to callers who can
     * manage the version (its ADMIN members, or the owning project's ADMIN,
     * or a platform ADMIN) — without the check, any authenticated user could
     * grant themselves access to any version.
     */
    @Transactional
    public VersionMember addRole(UUID versionId, AddVersionMemberRoleRequestDTO data) {
        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));
        accessGuard.assertCanManageVersion(versionId);
        validateRoleShape(data);

        User user = userRepository.findById(data.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isActive()) {
            throw new IllegalArgumentException("User is not active");
        }

        UUID projectId = projectRepository.findIdByVersionId(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version is not attached to any project"));
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, data.userId())) {
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
        if (data == null || data.memberType() == null) {
            throw new IllegalArgumentException("memberType cannot be null");
        }
        if (data.memberType() == VersionMemberType.EDITOR && data.vertical() == null) {
            throw new IllegalArgumentException("EDITOR roles require a vertical");
        }
        if (data.memberType() != VersionMemberType.EDITOR && data.vertical() != null) {
            throw new IllegalArgumentException(
                    data.memberType() + " roles apply to the whole version, not one vertical");
        }
    }

    /**
     * Every member of the version with their roles. As permissive as reading
     * the version itself: any version role or project membership is enough.
     */
    @Transactional(readOnly = true)
    public List<VersionMember> listMembers(UUID versionId) {
        if (!versionRepository.existsById(versionId)) {
            throw new VersionNotFoundException(versionId);
        }
        accessGuard.assertCanViewVersion(versionId);
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
        if (!versionRepository.existsById(versionId)) {
            throw new VersionNotFoundException(versionId);
        }
        accessGuard.assertCanManageVersion(versionId);

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
     * version's owning project. A version role only ever adds capabilities
     * on top of project membership (see {@code ProjectAccessGuard}), so
     * granting one to someone outside the project would be a permission with
     * no project-level access underneath it to narrow.
     */
    @Transactional(readOnly = true)
    public List<VersionMemberCandidateDTO> listMemberCandidates(UUID versionId, String q) {
        if (!versionRepository.existsById(versionId)) {
            throw new VersionNotFoundException(versionId);
        }
        accessGuard.assertCanManageVersion(versionId);
        UUID projectId = projectRepository.findIdByVersionId(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version is not attached to any project"));

        String needle = q == null ? null : q.trim().toLowerCase();
        return projectMemberRepository.findByProjectIdWithUser(projectId).stream()
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
