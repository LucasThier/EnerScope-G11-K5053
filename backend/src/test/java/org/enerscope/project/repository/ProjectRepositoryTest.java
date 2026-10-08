package org.enerscope.project.repository;

import org.enerscope.organization.model.Organization;
import org.enerscope.project.dto.ProjectSummaryDTO;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class ProjectRepositoryTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Organization organization;
    private User member;

    @BeforeEach
    void setUp() {
        organization = entityManager.persist(new Organization("Acme"));
        member = entityManager.persist(
                new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER));
    }

    private Project project(String name) {
        Project project = new Project(name, "A project", organization);
        organization.addProject(project);
        return entityManager.persist(project);
    }

    private ProjectMember attach(Project project, User user) {
        ProjectMember projectMember = new ProjectMember(user, project);
        project.addMember(projectMember);
        return entityManager.persist(projectMember);
    }

    private ProjectMember attachAs(Project project, User user, ProjectMemberType... types) {
        ProjectMember projectMember = new ProjectMember(user, project);
        for (ProjectMemberType type : types) {
            projectMember.addRole(new ProjectMemberRole(
                    type.name(), type, ProjectMember.defaultPermissionsFor(type)));
        }
        project.addMember(projectMember);
        return entityManager.persist(projectMember);
    }

    private Version attachVersion(Project project, String name) {
        Version version = new Version(name, null, null, null, null, null);
        project.addVersion(version);
        return entityManager.persist(version);
    }

    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void findSummariesReturnsAnActiveProject() {
        project("Grid Expansion");
        sync();

        List<ProjectSummaryDTO> summaries = projectRepository.findSummaries(member.getId(), null);

        assertEquals(1, summaries.size());
        assertEquals("Grid Expansion", summaries.get(0).name());
    }

    @Test
    void findSummariesExcludesADeactivatedProject() {
        Project project = project("Grid Expansion");
        project.deactivate();
        sync();

        assertTrue(projectRepository.findSummaries(member.getId(), null).isEmpty());
    }

    @Test
    void findSummariesCountsOnlyActiveMembers() {
        Project project = project("Grid Expansion");
        User second = entityManager.persist(
                new User("joe@enerscope.org", "Joe", "Roe", "hashed", PlatformRole.USER));
        attach(project, member);
        ProjectMember leaving = attach(project, second);
        leaving.deactivate();
        sync();

        List<ProjectSummaryDTO> summaries = projectRepository.findSummaries(member.getId(), null);

        assertEquals(1, summaries.size());
        assertEquals(1L, summaries.get(0).memberCount());
    }

    @Test
    void findSummariesForMemberExcludesADeactivatedProject() {
        Project project = project("Grid Expansion");
        attach(project, member);
        project.deactivate();
        sync();

        assertTrue(projectRepository.findSummariesForMember(member.getId(), null).isEmpty());
    }

    @Test
    void findSummariesForMemberReturnsAnActiveProject() {
        Project project = project("Grid Expansion");
        attach(project, member);
        sync();

        List<ProjectSummaryDTO> summaries = projectRepository.findSummariesForMember(member.getId(), null);

        assertEquals(1, summaries.size());
        assertEquals("Grid Expansion", summaries.get(0).name());
    }

    @Test
    void findIdByVersionIdReturnsTheOwningProject() {
        Project project = project("Grid Expansion");
        Version version = attachVersion(project, "v1");
        sync();

        assertEquals(Optional.of(project.getId()), projectRepository.findIdByVersionId(version.getId()));
    }

    @Test
    void findIdByVersionIdIgnoresADeactivatedVersion() {
        Project project = project("Grid Expansion");
        Version version = attachVersion(project, "v1");
        version.deactivate();
        sync();

        assertTrue(projectRepository.findIdByVersionId(version.getId()).isEmpty());
    }

    @Test
    void findIdByVersionIdIgnoresAVersionOfADeactivatedProject() {
        Project project = project("Grid Expansion");
        Version version = attachVersion(project, "v1");
        project.deactivate();
        sync();

        assertTrue(projectRepository.findIdByVersionId(version.getId()).isEmpty());
    }

    @Test
    void existsByIdAndActiveTrueIsTrueForAnActiveProject() {
        Project project = project("Grid Expansion");
        sync();

        assertTrue(projectRepository.existsByIdAndActiveTrue(project.getId()));
    }

    @Test
    void existsByIdAndActiveTrueIsFalseOnceTheProjectIsDeactivated() {
        Project project = project("Grid Expansion");
        project.deactivate();
        sync();

        assertFalse(projectRepository.existsByIdAndActiveTrue(project.getId()));
    }

    @Test
    void existsByIdAndActiveTrueIsFalseForAnUnknownId() {
        assertFalse(projectRepository.existsByIdAndActiveTrue(UUID.randomUUID()));
    }
    @Test
    void findSummariesExcludesProjectsOfADeactivatedOrganization() {
        project("Grid Expansion");
        organization.deactivate();
        sync();

        assertTrue(projectRepository.findSummaries(member.getId(), null).isEmpty());
    }

    @Test
    void findSummariesForMemberExcludesProjectsOfADeactivatedOrganization() {
        Project project = project("Grid Expansion");
        attach(project, member);
        organization.deactivate();
        sync();

        assertTrue(projectRepository.findSummariesForMember(member.getId(), null).isEmpty());
    }

    @Test
    void findSummariesForMemberExposesTheCallerRole() {
        Project led = project("Grid Expansion");
        Project edited = project("Solar Farm");
        User other = entityManager.persist(
                new User("joe@enerscope.org", "Joe", "Roe", "hashed", PlatformRole.USER));
        attachAs(led, member, ProjectMemberType.ADMIN);
        attachAs(edited, member, ProjectMemberType.EDITOR);
        attachAs(edited, other, ProjectMemberType.ADMIN);
        sync();

        List<ProjectSummaryDTO> summaries = projectRepository.findSummariesForMember(member.getId(), null);

        assertEquals(2, summaries.size());
        assertEquals(ProjectMemberType.ADMIN, roleOf(summaries, "Grid Expansion"));
        assertEquals(ProjectMemberType.EDITOR, roleOf(summaries, "Solar Farm"));
    }

    @Test
    void findSummariesExposesNullRoleWhenCallerIsNotAMember() {
        Project project = project("Grid Expansion");
        User platformAdmin = entityManager.persist(
                new User("admin@enerscope.org", "Ada", "Min", "hashed", PlatformRole.ADMIN));
        attachAs(project, member, ProjectMemberType.ADMIN);
        sync();

        List<ProjectSummaryDTO> summaries = projectRepository.findSummaries(platformAdmin.getId(), null);

        assertEquals(1, summaries.size());
        assertNull(summaries.get(0).myRole());
    }

    @Test
    void findSummariesExposesTheCallerRoleWhenPlatformAdminIsAMember() {
        Project project = project("Grid Expansion");
        User platformAdmin = entityManager.persist(
                new User("admin@enerscope.org", "Ada", "Min", "hashed", PlatformRole.ADMIN));
        attachAs(project, platformAdmin, ProjectMemberType.EDITOR);
        sync();

        List<ProjectSummaryDTO> summaries = projectRepository.findSummaries(platformAdmin.getId(), null);

        assertEquals(1, summaries.size());
        assertEquals(ProjectMemberType.EDITOR, summaries.get(0).myRole());
    }

    @Test
    void findSummariesForMemberPrefersAdminWhenTheCallerHoldsTwoRoles() {
        Project project = project("Grid Expansion");
        attachAs(project, member, ProjectMemberType.EDITOR, ProjectMemberType.ADMIN);
        sync();

        List<ProjectSummaryDTO> summaries = projectRepository.findSummariesForMember(member.getId(), null);

        assertEquals(1, summaries.size());
        assertEquals(ProjectMemberType.ADMIN, summaries.get(0).myRole());
    }

    private ProjectMemberType roleOf(List<ProjectSummaryDTO> summaries, String name) {
        return summaries.stream()
                .filter(summary -> summary.name().equals(name))
                .findFirst()
                .orElseThrow()
                .myRole();
    }
}
