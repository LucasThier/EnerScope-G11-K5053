package org.enerscope.version.service;

import java.time.Instant;
import java.util.*;
import org.enerscope.common.*;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.*;
import org.enerscope.project.model.enums.*;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.dto.VersionSummaryDTO;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VersionQueryServiceTest {
    final ProjectRepository projects = mock(ProjectRepository.class);
    final VersionQueryService service = new VersionQueryService(projects, mock(AppLogger.class));
    final UUID projectId = UUID.randomUUID();
    Project project;
    User user;

    @BeforeEach void setup() {
        user = User.fromJwtClaims(UUID.randomUUID(), "reader@example.com", "Reader", "User");
        project = new Project("Example", "Example", new Organization("Example"));
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        var auth = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
        auth.setDetails(new Session("test", user, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    ProjectMember grant(ProjectMemberPermission... permissions) {
        var member = new ProjectMember(user, project);
        member.addRole(new ProjectMemberRole("Custom", ProjectMemberType.EDITOR, Set.of(permissions)));
        project.addMember(member);
        return member;
    }
    Version version(String name, String time) {
        var version = new Version();
        version.setName(name);
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(version, "lastModified", Instant.parse(time));
        project.addVersion(version);
        return version;
    }
    @Test void anonymousCannotListVersions() {
        SecurityContextHolder.clearContext();
        assertThrows(UnauthorizedException.class, () -> service.list(projectId));
        verify(projects, never()).findById(any());
    }
    @Test void outsiderCannotListVersions() {
        assertThrows(ForbiddenException.class, () -> service.list(projectId));
    }
    @Test void editPermissionAloneDoesNotGrantView() {
        grant(ProjectMemberPermission.EDIT_PROJECT);
        assertThrows(ForbiddenException.class, () -> service.list(projectId));
    }
    @Test void inactiveMembershipCannotListVersions() {
        grant(ProjectMemberPermission.VIEW_PROJECT).deactivate();
        assertThrows(ForbiddenException.class, () -> service.list(projectId));
    }
    @Test void inactiveRoleCannotListVersions() {
        grant(ProjectMemberPermission.VIEW_PROJECT).getRoles().forEach(BaseEntity::deactivate);
        assertThrows(ForbiddenException.class, () -> service.list(projectId));
    }
    @Test void viewerGetsOnlyActiveVersionsNewestFirst() {
        grant(ProjectMemberPermission.VIEW_PROJECT);
        version("Old", "2030-01-01T00:00:00Z");
        version("Hidden", "2032-01-01T00:00:00Z").deactivate();
        version("New", "2031-01-01T00:00:00Z");
        var result = service.list(projectId);
        assertEquals(List.of("New", "Old"), result.stream().map(VersionSummaryDTO::name).toList());
        assertTrue(result.getFirst().nodes().isEmpty());
    }
    @Test void adminCanListWithoutMembershipAndReceivesNodeSummary() {
        user.updatePlatformRole(PlatformRole.ADMIN);
        var version = org.enerscope.economic.service.EconomicExample.version();
        project.addVersion(version);
        var result = service.list(projectId);
        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().nodes().size());
        assertEquals(version.getNodeSnapshot().getFirst().getId(), result.getFirst().nodes().getFirst().id());
    }
    @Test void missingProjectReturnsNotFound() {
        when(projects.findById(projectId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.list(projectId));
    }
    @Test void anotherProjectDoesNotLeakVersions() {
        user.updatePlatformRole(PlatformRole.ADMIN);
        version("Private", "2030-01-01T00:00:00Z");
        UUID otherId = UUID.randomUUID();
        when(projects.findById(otherId)).thenReturn(Optional.of(new Project("Other", "Other", new Organization("Other"))));
        assertTrue(service.list(otherId).isEmpty());
    }
}
