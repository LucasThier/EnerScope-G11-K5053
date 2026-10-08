package org.enerscope.project.repository;

import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

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
        return attach(project, user, ProjectMemberType.ADMIN);
    }

    private ProjectMember attach(Project project, User user, ProjectMemberType type) {
        ProjectMember member = new ProjectMember(user, project);
        member.addRole(new ProjectMemberRole(type.name(), type, ProjectMember.defaultPermissionsFor(type)));
        project.addMember(member);
        return entityManager.persist(member);
    }

    private User user(String mail) {
        return entityManager.persist(new User(mail, "First", "Last", "hashed", PlatformRole.USER));
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

    @Test
    void findsAMembershipByIdWithinItsProject() {
        Project project = project("Grid Expansion", acme);
        ProjectMember member = attach(project, jane);
        sync();

        assertTrue(projectMemberRepository.findByIdAndProjectId(member.getId(), project.getId()).isPresent());
    }

    @Test
    void doesNotFindAMembershipThroughAnotherProject() {
        Project project = project("Grid Expansion", acme);
        Project other = project("Pipeline Review", acme);
        ProjectMember member = attach(project, jane);
        sync();

        assertTrue(projectMemberRepository.findByIdAndProjectId(member.getId(), other.getId()).isEmpty());
        assertTrue(projectMemberRepository.findByIdAndProjectId(UUID.randomUUID(), project.getId()).isEmpty());
    }

    @Test
    void countsTheActiveAdminsOfAProject() {
        Project project = project("Grid Expansion", acme);
        attach(project, jane, ProjectMemberType.ADMIN);
        attach(project, user("joe@enerscope.org"), ProjectMemberType.ADMIN);
        sync();

        assertEquals(2L, projectMemberRepository.countAdminsByProject(project.getId()));
    }

    @Test
    void doesNotCountEditorsAsAdmins() {
        Project project = project("Grid Expansion", acme);
        attach(project, jane, ProjectMemberType.ADMIN);
        attach(project, user("joe@enerscope.org"), ProjectMemberType.EDITOR);
        sync();

        assertEquals(1L, projectMemberRepository.countAdminsByProject(project.getId()));
    }

    @Test
    void doesNotCountAnAdminWhoseMembershipIsInactive() {
        Project project = project("Grid Expansion", acme);
        attach(project, jane, ProjectMemberType.ADMIN);
        ProjectMember inactive = attach(project, user("joe@enerscope.org"), ProjectMemberType.ADMIN);
        inactive.deactivate();
        sync();

        assertEquals(1L, projectMemberRepository.countAdminsByProject(project.getId()));
    }

    @Test
    void doesNotCountAnAdminWhoseUserIsInactive() {
        Project project = project("Grid Expansion", acme);
        attach(project, jane, ProjectMemberType.ADMIN);
        User deactivated = user("joe@enerscope.org");
        deactivated.deactivate();
        attach(project, deactivated, ProjectMemberType.ADMIN);
        sync();

        assertEquals(1L, projectMemberRepository.countAdminsByProject(project.getId()));
    }

    @Test
    void doesNotCountTheAdminsOfAnotherProject() {
        Project project = project("Grid Expansion", acme);
        Project other = project("Pipeline Review", acme);
        attach(project, jane, ProjectMemberType.ADMIN);
        attach(other, user("joe@enerscope.org"), ProjectMemberType.ADMIN);
        attach(other, user("ann@enerscope.org"), ProjectMemberType.ADMIN);
        sync();

        assertEquals(1L, projectMemberRepository.countAdminsByProject(project.getId()));
    }

    @Test
    void countsZeroAdminsForAProjectWithNone() {
        Project project = project("Grid Expansion", acme);
        attach(project, jane, ProjectMemberType.EDITOR);
        sync();

        assertEquals(0L, projectMemberRepository.countAdminsByProject(project.getId()));
    }

    @Test
    void anInactiveMembershipStillCountsAsExistingForTheUniquePair() {
        Project project = project("Grid Expansion", acme);
        ProjectMember member = attach(project, jane, ProjectMemberType.EDITOR);
        member.deactivate();
        sync();

        assertTrue(projectMemberRepository.existsByProjectIdAndUserId(project.getId(), jane.getId()));
    }

    @Test
    void replacingTheRoleOfAPersistedMemberLeavesASingleRoleWithTheNewPermissions() {
        Project project = project("Grid Expansion", acme);
        ProjectMember member = attach(project, jane, ProjectMemberType.ADMIN);
        sync();

        ProjectMember loaded = projectMemberRepository.findById(member.getId()).orElseThrow();
        loaded.changeRole(ProjectMemberType.EDITOR, ProjectMember.defaultPermissionsFor(ProjectMemberType.EDITOR));
        projectMemberRepository.save(loaded);
        sync();

        ProjectMember reloaded = projectMemberRepository.findById(member.getId()).orElseThrow();
        assertEquals(1, reloaded.getRoles().size());
        ProjectMemberRole role = reloaded.getRoles().iterator().next();
        assertEquals(ProjectMemberType.EDITOR, role.getMemberType());
        assertEquals(ProjectMember.defaultPermissionsFor(ProjectMemberType.EDITOR), role.getPermissions());
        assertEquals(1L, roleCount());
    }

    @Test
    void deletingAMembershipByEntityCascadesToItsRoles() {
        Project project = project("Grid Expansion", acme);
        ProjectMember member = attach(project, jane, ProjectMemberType.ADMIN);
        sync();
        assertEquals(1L, roleCount());

        ProjectMember loaded = projectMemberRepository.findById(member.getId()).orElseThrow();
        projectMemberRepository.delete(loaded);
        sync();

        assertTrue(projectMemberRepository.findById(member.getId()).isEmpty());
        assertEquals(0L, roleCount());
    }

    private long roleCount() {
        return entityManager.getEntityManager()
                .createQuery("SELECT COUNT(r) FROM ProjectMemberRole r", Long.class)
                .getSingleResult();
    }
}
