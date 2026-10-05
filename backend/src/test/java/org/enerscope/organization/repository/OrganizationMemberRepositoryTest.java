package org.enerscope.organization.repository;

import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.project.dto.ProjectMemberCandidateDTO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class OrganizationMemberRepositoryTest {

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Organization acme;
    private Organization globex;
    private Project project;

    @BeforeEach
    void setUp() {
        acme = entityManager.persist(new Organization("Acme"));
        globex = entityManager.persist(new Organization("Globex"));
        project = new Project("Grid Expansion", "A project", acme);
        acme.addProject(project);
        entityManager.persist(project);
    }

    private User user(String mail, String firstName, String lastName) {
        return entityManager.persist(new User(mail, firstName, lastName, "hashed", PlatformRole.USER));
    }

    private OrganizationMember join(Organization organization, User user) {
        OrganizationMember member = new OrganizationMember(user, organization);
        organization.addMember(member);
        return entityManager.persist(member);
    }

    private ProjectMember attach(User user) {
        ProjectMember member = new ProjectMember(user, project);
        member.addRole(new ProjectMemberRole(ProjectMemberType.EDITOR.name(), ProjectMemberType.EDITOR,
                ProjectMember.defaultPermissionsFor(ProjectMemberType.EDITOR)));
        project.addMember(member);
        return entityManager.persist(member);
    }

    private List<ProjectMemberCandidateDTO> candidates(String pattern) {
        entityManager.flush();
        entityManager.clear();
        return organizationMemberRepository.findProjectMemberCandidates(acme.getId(), project.getId(), pattern);
    }

    @Test
    void returnsTheActiveOrganizationMembersWhoAreNotOnTheProject() {
        User jane = user("jane@enerscope.org", "Jane", "Doe");
        join(acme, jane);

        List<ProjectMemberCandidateDTO> found = candidates("%");

        assertEquals(1, found.size());
        assertEquals(jane.getId(), found.get(0).id());
        assertEquals("Jane", found.get(0).firstName());
        assertEquals("Doe", found.get(0).lastName());
        assertEquals("jane@enerscope.org", found.get(0).mail());
    }

    @Test
    void excludesUsersAlreadyOnTheProject() {
        User jane = user("jane@enerscope.org", "Jane", "Doe");
        User joe = user("joe@enerscope.org", "Joe", "Roe");
        join(acme, jane);
        join(acme, joe);
        attach(jane);

        List<ProjectMemberCandidateDTO> found = candidates("%");

        assertEquals(1, found.size());
        assertEquals("joe@enerscope.org", found.get(0).mail());
    }

    @Test
    void excludesUsersWhoseProjectMembershipIsInactive() {
        User jane = user("jane@enerscope.org", "Jane", "Doe");
        join(acme, jane);
        attach(jane).deactivate();

        assertTrue(candidates("%").isEmpty());
    }

    @Test
    void excludesUsersWhoAreOnlyInAnotherOrganization() {
        join(acme, user("jane@enerscope.org", "Jane", "Doe"));
        join(globex, user("joe@enerscope.org", "Joe", "Roe"));

        List<ProjectMemberCandidateDTO> found = candidates("%");

        assertEquals(1, found.size());
        assertEquals("jane@enerscope.org", found.get(0).mail());
    }

    @Test
    void excludesInactiveOrganizationMemberships() {
        join(acme, user("jane@enerscope.org", "Jane", "Doe")).deactivate();
        join(acme, user("joe@enerscope.org", "Joe", "Roe"));

        List<ProjectMemberCandidateDTO> found = candidates("%");

        assertEquals(1, found.size());
        assertEquals("joe@enerscope.org", found.get(0).mail());
    }

    @Test
    void excludesInactiveUsers() {
        User jane = user("jane@enerscope.org", "Jane", "Doe");
        jane.deactivate();
        join(acme, jane);
        join(acme, user("joe@enerscope.org", "Joe", "Roe"));

        List<ProjectMemberCandidateDTO> found = candidates("%");

        assertEquals(1, found.size());
        assertEquals("joe@enerscope.org", found.get(0).mail());
    }

    @Test
    void returnsNothingForAnotherProjectsOrganization() {
        join(acme, user("jane@enerscope.org", "Jane", "Doe"));
        entityManager.flush();
        entityManager.clear();

        assertTrue(organizationMemberRepository
                .findProjectMemberCandidates(globex.getId(), project.getId(), "%").isEmpty());
    }

    @Test
    void keepsUsersWhoAreOnlyOnAnotherProjectOfTheSameOrganization() {
        User jane = user("jane@enerscope.org", "Jane", "Doe");
        join(acme, jane);
        Project other = new Project("Pipeline Review", "Another project", acme);
        acme.addProject(other);
        entityManager.persist(other);
        ProjectMember onOther = new ProjectMember(jane, other);
        onOther.addRole(new ProjectMemberRole(ProjectMemberType.EDITOR.name(), ProjectMemberType.EDITOR,
                ProjectMember.defaultPermissionsFor(ProjectMemberType.EDITOR)));
        other.addMember(onOther);
        entityManager.persist(onOther);

        List<ProjectMemberCandidateDTO> found = candidates("%");

        assertEquals(1, found.size());
        assertEquals(jane.getId(), found.get(0).id());
    }

    @Test
    void filtersByFirstNameLastNameOrMailIgnoringCase() {
        join(acme, user("a@enerscope.org", "Marta", "Quiroga"));
        join(acme, user("b@enerscope.org", "Pedro", "Martinez"));
        join(acme, user("martin.z@enerscope.org", "Lucia", "Paz"));
        join(acme, user("d@enerscope.org", "Carlos", "Lopez"));

        List<ProjectMemberCandidateDTO> found = candidates("%mart%");

        assertEquals(3, found.size());
        assertTrue(found.stream().noneMatch(c -> c.firstName().equals("Carlos")));
    }

    @Test
    void treatsPercentAndUnderscoreInTheEscapedPatternAsLiterals() {
        join(acme, user("a@enerscope.org", "Ana_Maria", "Ruiz"));
        join(acme, user("b@enerscope.org", "AnaXMaria", "Ruiz"));

        List<ProjectMemberCandidateDTO> found = candidates("%ana\\_maria%");

        assertEquals(1, found.size());
        assertEquals("a@enerscope.org", found.get(0).mail());
    }

    @Test
    void ordersByLastNameThenFirstName() {
        join(acme, user("1@enerscope.org", "Zoe", "Alvarez"));
        join(acme, user("2@enerscope.org", "Ana", "Alvarez"));
        join(acme, user("3@enerscope.org", "Bea", "Aguirre"));

        List<ProjectMemberCandidateDTO> found = candidates("%");

        assertEquals(List.of("3@enerscope.org", "2@enerscope.org", "1@enerscope.org"),
                found.stream().map(ProjectMemberCandidateDTO::mail).toList());
    }
}
