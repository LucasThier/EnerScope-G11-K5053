package org.enerscope.project.repository;

import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class ProjectMemberRepositoryTest {

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Organization acme;
    private Organization globex;
    private User jane;

    @BeforeEach
    void setUp() {
        acme = entityManager.persist(new Organization("Acme"));
        globex = entityManager.persist(new Organization("Globex"));
        jane = entityManager.persist(
                new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER));
    }

    private Project project(String name, Organization organization) {
        Project project = new Project(name, "A project", organization);
        organization.addProject(project);
        return entityManager.persist(project);
    }

    private ProjectMember attach(Project project, User user) {
        ProjectMember member = new ProjectMember(user, project);
        member.addRole(new ProjectMemberRole(
                ProjectMemberType.ADMIN.name(), ProjectMemberType.ADMIN,
                EnumSet.of(ProjectMemberPermission.MANAGE_PROJECT,
                        ProjectMemberPermission.EDIT_PROJECT,
                        ProjectMemberPermission.VIEW_PROJECT)));
        project.addMember(member);
        return entityManager.persist(member);
    }

    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void findsTheMembershipsOfAUserInTheProjectsOfOneOrganization() {
        Project first = project("Grid Expansion", acme);
        Project second = project("Pipeline Review", acme);
        attach(first, jane);
        attach(second, jane);
        sync();

        List<ProjectMember> found = projectMemberRepository.findByUserInOrganization(jane.getId(), acme.getId());

        assertEquals(2, found.size());
    }

    @Test
    void excludesProjectsOfAnotherOrganization() {
        attach(project("Grid Expansion", acme), jane);
        attach(project("Offshore Terminal", globex), jane);
        sync();

        List<ProjectMember> found = projectMemberRepository.findByUserInOrganization(jane.getId(), acme.getId());

        assertEquals(1, found.size());
        assertEquals("Grid Expansion", found.get(0).getProject().getName());
    }

    @Test
    void excludesTheMembershipsOfOtherUsers() {
        User joe = entityManager.persist(
                new User("joe@enerscope.org", "Joe", "Roe", "hashed", PlatformRole.USER));
        Project project = project("Grid Expansion", acme);
        attach(project, jane);
        attach(project, joe);
        sync();

        List<ProjectMember> found = projectMemberRepository.findByUserInOrganization(jane.getId(), acme.getId());

        assertEquals(1, found.size());
        assertEquals("jane@enerscope.org", found.get(0).getUser().getMail());
    }

    @Test
    void returnsEmptyWhenTheUserIsInNoProjectOfThatOrganization() {
        attach(project("Offshore Terminal", globex), jane);
        sync();

        assertTrue(projectMemberRepository.findByUserInOrganization(jane.getId(), acme.getId()).isEmpty());
    }

    @Test
    void deletingTheFoundMembershipsCascadesToTheirRoles() {
        attach(project("Grid Expansion", acme), jane);
        sync();

        assertEquals(1L, roleCount());

        List<ProjectMember> found = projectMemberRepository.findByUserInOrganization(jane.getId(), acme.getId());
        assertEquals(1, found.size());
        projectMemberRepository.deleteAll(found);
        sync();

        assertTrue(projectMemberRepository.findByUserInOrganization(jane.getId(), acme.getId()).isEmpty());
        assertEquals(0L, roleCount());
    }

    private long roleCount() {
        return entityManager.getEntityManager()
                .createQuery("SELECT COUNT(r) FROM ProjectMemberRole r", Long.class)
                .getSingleResult();
    }
}
