package org.enerscope.version.service;

import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.ForbiddenException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.project.service.ProjectAccessGuard;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
import org.enerscope.version.dto.AddVersionMemberRoleRequestDTO;
import org.enerscope.version.dto.VersionMemberCandidateDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberType;
import org.enerscope.version.repository.VersionMemberRepository;
import org.enerscope.version.repository.VersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionMemberServiceTest {

    @Mock
    private VersionRepository versionRepository;
    @Mock
    private VersionMemberRepository versionMemberRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AppLogger logger;

    private VersionAccessGuard accessGuard;
    private VersionMemberService service;

    @BeforeEach
    void setUp() {
        // Real guards over the mocked repositories, same reasoning as
        // ProjectServiceTest: these tests assert on authorization outcomes.
        ProjectAccessGuard projectAccessGuard = new ProjectAccessGuard(projectMemberRepository, logger);
        accessGuard = new VersionAccessGuard(projectRepository, versionMemberRepository, projectAccessGuard, logger);
        service = new VersionMemberService(
                versionRepository, versionMemberRepository, projectRepository, projectMemberRepository,
                userRepository, accessGuard, logger);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- addRole ---------------------------------------------------------

    @Test
    void addRoleGrantsAnEditorScopedToOneVertical() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Version version = new Version("v1", null, null, null, null, null);
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        authenticateAs(admin());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).thenReturn(true);
        when(versionMemberRepository.findByVersionIdAndUserId(versionId, userId)).thenReturn(Optional.empty());
        when(versionMemberRepository.save(any(VersionMember.class))).thenAnswer(inv -> inv.getArgument(0));

        VersionMember saved = service.addRole(versionId,
                new AddVersionMemberRoleRequestDTO(userId, VersionMemberType.EDITOR, VerticalEnum.EXTRACTION));

        assertEquals(1, saved.getRoles().size());
        VersionMemberRole role = saved.getRoles().iterator().next();
        assertEquals(VersionMemberType.EDITOR, role.getMemberType());
        assertEquals(VerticalEnum.EXTRACTION, role.getVertical());
    }

    @Test
    void addRoleAddsASecondRoleToAnExistingMemberInsteadOfReplacingTheFirst() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Version version = new Version("v1", null, null, null, null, null);
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        VersionMember existing = new VersionMember(user, version);
        existing.addRole(new VersionMemberRole("EDITOR", VersionMemberType.EDITOR,
                VersionMember.defaultPermissionsFor(VersionMemberType.EDITOR), VerticalEnum.EXTRACTION));
        authenticateAs(admin());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).thenReturn(true);
        when(versionMemberRepository.findByVersionIdAndUserId(versionId, userId)).thenReturn(Optional.of(existing));
        when(versionMemberRepository.save(any(VersionMember.class))).thenAnswer(inv -> inv.getArgument(0));

        VersionMember saved = service.addRole(versionId,
                new AddVersionMemberRoleRequestDTO(userId, VersionMemberType.SIMULATOR, null));

        assertEquals(2, saved.getRoles().size());
    }

    @Test
    void addRoleRejectsEditorWithoutAVertical() {
        UUID versionId = UUID.randomUUID();
        Version version = new Version("v1", null, null, null, null, null);
        authenticateAs(admin());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        assertThrows(IllegalArgumentException.class, () -> service.addRole(versionId,
                new AddVersionMemberRoleRequestDTO(UUID.randomUUID(), VersionMemberType.EDITOR, null)));
        verify(versionMemberRepository, never()).save(any());
    }

    @Test
    void addRoleRejectsAdminWithAVertical() {
        UUID versionId = UUID.randomUUID();
        Version version = new Version("v1", null, null, null, null, null);
        authenticateAs(admin());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        assertThrows(IllegalArgumentException.class, () -> service.addRole(versionId,
                new AddVersionMemberRoleRequestDTO(UUID.randomUUID(), VersionMemberType.ADMIN, VerticalEnum.EXTRACTION)));
        verify(versionMemberRepository, never()).save(any());
    }

    @Test
    void addRoleRejectsAUserOutsideTheVersionsProject() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Version version = new Version("v1", null, null, null, null, null);
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        authenticateAs(admin());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.addRole(versionId,
                new AddVersionMemberRoleRequestDTO(userId, VersionMemberType.SIMULATOR, null)));
        verify(versionMemberRepository, never()).save(any());
    }

    @Test
    void addRoleRejectsADuplicateRole() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Version version = new Version("v1", null, null, null, null, null);
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        VersionMember existing = new VersionMember(user, version);
        existing.addRole(new VersionMemberRole("EDITOR", VersionMemberType.EDITOR,
                VersionMember.defaultPermissionsFor(VersionMemberType.EDITOR), VerticalEnum.EXTRACTION));
        authenticateAs(admin());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).thenReturn(true);
        when(versionMemberRepository.findByVersionIdAndUserId(versionId, userId)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> service.addRole(versionId,
                new AddVersionMemberRoleRequestDTO(userId, VersionMemberType.EDITOR, VerticalEnum.EXTRACTION)));
        verify(versionMemberRepository, never()).save(any());
    }

    @Test
    void addRoleRejectsAProjectEditorCallerWith403() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Version version = new Version("v1", null, null, null, null, null);
        User caller = regularUser("editor@enerscope.org");
        authenticateAs(caller);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(versionMemberRepository.findByVersionIdAndUserId(versionId, caller.getId())).thenReturn(Optional.empty());
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(projectMemberWithEditPermission()));

        assertThrows(ForbiddenException.class, () -> service.addRole(versionId,
                new AddVersionMemberRoleRequestDTO(UUID.randomUUID(), VersionMemberType.SIMULATOR, null)));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void addRoleRejectsUnknownVersion() {
        UUID versionId = UUID.randomUUID();
        when(versionRepository.findById(versionId)).thenReturn(Optional.empty());

        assertThrows(VersionNotFoundException.class, () -> service.addRole(versionId,
                new AddVersionMemberRoleRequestDTO(UUID.randomUUID(), VersionMemberType.SIMULATOR, null)));
    }

    // ---- listMembers -------------------------------------------------------

    @Test
    void listMembersReturnsEveryMemberOfTheVersion() {
        UUID versionId = UUID.randomUUID();
        Version version = new Version("v1", null, null, null, null, null);
        authenticateAs(admin());
        when(versionRepository.existsById(versionId)).thenReturn(true);
        VersionMember member = new VersionMember(new User("a@b.org", "A", "B", "h"), version);
        when(versionMemberRepository.findByVersionIdWithUser(versionId)).thenReturn(List.of(member));

        List<VersionMember> members = service.listMembers(versionId);

        assertEquals(1, members.size());
    }

    // ---- removeRole ----------------------------------------------------------

    @Test
    void removeRoleRemovesOnlyTheGivenRoleWhenOthersRemain() {
        UUID versionId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Version version = new Version("v1", null, null, null, null, null);
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        VersionMember member = new VersionMember(user, version);
        VersionMemberRole editorRole = new VersionMemberRole("EDITOR", VersionMemberType.EDITOR,
                VersionMember.defaultPermissionsFor(VersionMemberType.EDITOR), VerticalEnum.EXTRACTION);
        VersionMemberRole simulatorRole = new VersionMemberRole("SIMULATOR", VersionMemberType.SIMULATOR,
                VersionMember.defaultPermissionsFor(VersionMemberType.SIMULATOR), null);
        member.addRole(editorRole);
        member.addRole(simulatorRole);
        ReflectionTestUtils.setField(editorRole, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(simulatorRole, "id", UUID.randomUUID());
        authenticateAs(admin());
        when(versionRepository.existsById(versionId)).thenReturn(true);
        when(versionMemberRepository.findByIdAndVersionId(memberId, versionId)).thenReturn(Optional.of(member));

        service.removeRole(versionId, memberId, simulatorRole.getId());

        assertEquals(1, member.getRoles().size());
        assertTrue(member.getRoles().contains(editorRole));
        verify(versionMemberRepository).save(member);
        verify(versionMemberRepository, never()).delete(any());
    }

    @Test
    void removeRoleDeletesTheMemberWhenItsLastRoleIsRemoved() {
        UUID versionId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Version version = new Version("v1", null, null, null, null, null);
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        VersionMember member = new VersionMember(user, version);
        VersionMemberRole role = new VersionMemberRole("SIMULATOR", VersionMemberType.SIMULATOR,
                VersionMember.defaultPermissionsFor(VersionMemberType.SIMULATOR), null);
        member.addRole(role);
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        authenticateAs(admin());
        when(versionRepository.existsById(versionId)).thenReturn(true);
        when(versionMemberRepository.findByIdAndVersionId(memberId, versionId)).thenReturn(Optional.of(member));

        service.removeRole(versionId, memberId, role.getId());

        verify(versionMemberRepository).delete(member);
        verify(versionMemberRepository, never()).save(any());
    }

    @Test
    void removeRoleAnswers404ForAnUnknownRole() {
        UUID versionId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Version version = new Version("v1", null, null, null, null, null);
        VersionMember member = new VersionMember(new User("a@b.org", "A", "B", "h"), version);
        authenticateAs(admin());
        when(versionRepository.existsById(versionId)).thenReturn(true);
        when(versionMemberRepository.findByIdAndVersionId(memberId, versionId)).thenReturn(Optional.of(member));

        assertThrows(EntityNotFoundException.class,
                () -> service.removeRole(versionId, memberId, UUID.randomUUID()));
    }

    // ---- listMemberCandidates --------------------------------------------

    @Test
    void listMemberCandidatesReturnsActiveProjectMembers() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Version version = new Version("v1", null, null, null, null, null);
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        ProjectMember projectMember = new ProjectMember(user, project);
        authenticateAs(admin());
        when(versionRepository.existsById(versionId)).thenReturn(true);
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.findByProjectIdWithUser(projectId)).thenReturn(List.of(projectMember));

        List<VersionMemberCandidateDTO> candidates = service.listMemberCandidates(versionId, null);

        assertEquals(1, candidates.size());
        assertEquals(user.getMail(), candidates.get(0).mail());
    }

    // ---- helpers -----------------------------------------------------------

    private ProjectMember projectMemberWithEditPermission() {
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        User user = new User("editor@enerscope.org", "Ed", "Itor", "hashed");
        ProjectMember member = new ProjectMember(user, project);
        member.addRole(new org.enerscope.project.model.ProjectMemberRole(
                "EDITOR", org.enerscope.project.model.enums.ProjectMemberType.EDITOR,
                Set.of(org.enerscope.project.model.enums.ProjectMemberPermission.EDIT_PROJECT,
                        org.enerscope.project.model.enums.ProjectMemberPermission.VIEW_PROJECT)));
        return member;
    }

    private User admin() {
        return User.fromJwtClaims(UUID.randomUUID(), "admin@enerscope.org", "Admin", "User", PlatformRole.ADMIN);
    }

    private User regularUser(String mail) {
        return User.fromJwtClaims(UUID.randomUUID(), mail, "Jane", "Doe", PlatformRole.USER);
    }

    private void authenticateAs(User caller) {
        Session session = new Session("token", caller, Instant.now().plusSeconds(3600));
        var auth = new UsernamePasswordAuthenticationToken(caller, null, List.of());
        auth.setDetails(session);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
