package org.enerscope.project.repository;

import org.enerscope.organization.model.Organization;
import org.enerscope.project.dto.ProjectSummaryDTO;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
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

        List<ProjectSummaryDTO> summaries = projectRepository.findSummaries(null);

        assertEquals(1, summaries.size());
        assertEquals("Grid Expansion", summaries.get(0).name());
    }

    @Test
    void findSummariesExcludesADeactivatedProject() {
        Project project = project("Grid Expansion");
        project.deactivate();
        sync();

        assertTrue(projectRepository.findSummaries(null).isEmpty());
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

        List<ProjectSummaryDTO> summaries = projectRepository.findSummaries(null);

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
}
