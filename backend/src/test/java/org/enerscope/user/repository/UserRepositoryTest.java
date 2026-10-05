package org.enerscope.user.repository;

import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.dto.UserListItemDTO;
import org.enerscope.user.dto.UserOrganizationMembershipDTO;
import org.enerscope.user.dto.UserProjectMembershipDTO;
import org.enerscope.user.dto.UserSearchResultDTO;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persist(String mail, PlatformRole role, boolean active) {
        User user = new User(mail, "First", "Last", "hashed", role);
        if (!active) {
            user.deactivate();
        }
        return entityManager.persist(user);
    }

    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    private void join(User user, Organization organization) {
        OrganizationMember member = new OrganizationMember(user, organization);
        organization.addMember(member);
        entityManager.persist(member);
    }

    private void joinWithRole(User user, Organization organization, OrganizationMemberType memberType) {
        OrganizationMember member = new OrganizationMember(user, organization);
        member.addRole(new OrganizationMemberRole(
                memberType.name(), memberType, EnumSet.of(OrganizationMemberPermission.VIEW_ORGANIZATION)));
        organization.addMember(member);
        entityManager.persist(member);
    }

    private Project persistProject(String name, Organization organization) {
        Project project = new Project(name, "A project", organization);
        organization.addProject(project);
        return entityManager.persist(project);
    }

    private void attachToProject(User user, Project project, ProjectMemberType memberType) {
        ProjectMember member = new ProjectMember(user, project);
        member.addRole(new ProjectMemberRole(
                memberType.name(), memberType, EnumSet.of(ProjectMemberPermission.VIEW_PROJECT)));
        project.addMember(member);
        entityManager.persist(member);
    }

    @Test
    void listItemsCarryTheFieldsTheTableShows() {
        User jane = persist("list-jane@enerscope.org", PlatformRole.USER, true);
        jane.updateProfile(null, null, "Analyst");
        sync();

        List<UserListItemDTO> rows = userRepository.findListItems();

        assertEquals(1, rows.size());
        UserListItemDTO row = rows.get(0);
        assertEquals("list-jane@enerscope.org", row.mail());
        assertEquals("Analyst", row.jobTitle());
        assertEquals(PlatformRole.USER, row.platformRole());
        assertTrue(row.active());
    }

    @Test
    void listItemsIncludeDeactivatedAccounts() {
        persist("list-active@enerscope.org", PlatformRole.USER, true);
        persist("list-suspended@enerscope.org", PlatformRole.USER, false);
        sync();

        List<UserListItemDTO> rows = userRepository.findListItems();

        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(UserListItemDTO::active));
        assertTrue(rows.stream().anyMatch(row -> !row.active()));
    }

    @Test
    void listItemsCountTheOrganizationsEachUserBelongsTo() {
        User jane = persist("list-two-orgs@enerscope.org", PlatformRole.USER, true);
        join(jane, entityManager.persist(new Organization("Acme")));
        join(jane, entityManager.persist(new Organization("Globex")));
        sync();

        assertEquals(2L, userRepository.findListItems().get(0).organizationCount());
    }

    @Test
    void listItemsCountZeroForAnAccountWithNoOrganization() {
        persist("list-orphan@enerscope.org", PlatformRole.USER, true);
        sync();

        assertEquals(0L, userRepository.findListItems().get(0).organizationCount());
    }

    @Test
    void listItemsDoNotCountAnotherUsersOrganizations() {
        User jane = persist("list-aaa@enerscope.org", PlatformRole.USER, true);
        User joe = persist("list-bbb@enerscope.org", PlatformRole.USER, true);
        Organization acme = entityManager.persist(new Organization("Acme"));
        join(jane, acme);
        join(joe, acme);
        join(joe, entityManager.persist(new Organization("Globex")));
        sync();

        List<UserListItemDTO> rows = userRepository.findListItems();
        assertEquals(1L, rows.stream()
                .filter(row -> row.mail().equals("list-aaa@enerscope.org"))
                .findFirst().orElseThrow().organizationCount());
        assertEquals(2L, rows.stream()
                .filter(row -> row.mail().equals("list-bbb@enerscope.org"))
                .findFirst().orElseThrow().organizationCount());
    }

    @Test
    void countsTheActiveAdmins() {
        persist("count-admin-1@enerscope.org", PlatformRole.ADMIN, true);
        persist("count-admin-2@enerscope.org", PlatformRole.ADMIN, true);
        sync();

        assertEquals(2L, userRepository.countByPlatformRoleAndActiveTrue(PlatformRole.ADMIN));
    }

    @Test
    void excludesInactiveAdmins() {
        persist("count-active@enerscope.org", PlatformRole.ADMIN, true);
        persist("count-suspended@enerscope.org", PlatformRole.ADMIN, false);
        sync();

        assertEquals(1L, userRepository.countByPlatformRoleAndActiveTrue(PlatformRole.ADMIN));
    }

    @Test
    void excludesRegularUsers() {
        persist("count-only-admin@enerscope.org", PlatformRole.ADMIN, true);
        persist("count-user-1@enerscope.org", PlatformRole.USER, true);
        persist("count-user-2@enerscope.org", PlatformRole.USER, true);
        sync();

        assertEquals(1L, userRepository.countByPlatformRoleAndActiveTrue(PlatformRole.ADMIN));
    }

    @Test
    void countsZeroWhenEveryAdminIsInactive() {
        persist("count-all-suspended@enerscope.org", PlatformRole.ADMIN, false);
        persist("count-plain@enerscope.org", PlatformRole.USER, true);
        sync();

        assertEquals(0L, userRepository.countByPlatformRoleAndActiveTrue(PlatformRole.ADMIN));
    }
    @Test
    void searchFindsAnActiveAccountByItsExactAddress() {
        persist("search-jane@enerscope.org", PlatformRole.USER, true);
        sync();

        UserSearchResultDTO found = userRepository
                .findSearchResultByMail("search-jane@enerscope.org").orElseThrow();

        assertEquals("search-jane@enerscope.org", found.mail());
        assertEquals("First", found.firstName());
    }

    @Test
    void searchIgnoresCase() {
        persist("search-case@enerscope.org", PlatformRole.USER, true);
        sync();

        assertTrue(userRepository.findSearchResultByMail("SEARCH-CASE@EnerScope.ORG").isPresent());
    }

    @Test
    void searchNeverMatchesPartially() {
        persist("search-partial@enerscope.org", PlatformRole.USER, true);
        sync();

        assertTrue(userRepository.findSearchResultByMail("search-partial").isEmpty());
        assertTrue(userRepository.findSearchResultByMail("@enerscope.org").isEmpty());
    }

    @Test
    void searchSkipsASuspendedAccount() {
        persist("search-suspended@enerscope.org", PlatformRole.USER, false);
        sync();

        assertTrue(userRepository.findSearchResultByMail("search-suspended@enerscope.org").isEmpty());
    }

    @Test
    void organizationMembershipsCarryTheOrganizationAndTheRole() {
        User jane = persist("detail-jane@enerscope.org", PlatformRole.USER, true);
        Organization acme = entityManager.persist(new Organization("Acme"));
        joinWithRole(jane, acme, OrganizationMemberType.OWNER);
        sync();

        List<UserOrganizationMembershipDTO> rows =
                userRepository.findOrganizationMembershipsForUser(jane.getId());

        assertEquals(1, rows.size());
        assertEquals("Acme", rows.get(0).organizationName());
        assertEquals(OrganizationMemberType.OWNER, rows.get(0).memberType());
    }

    @Test
    void organizationMembershipsIncludeASuspendedOrganizationMarked() {
        User jane = persist("detail-suspended-org@enerscope.org", PlatformRole.USER, true);
        Organization acme = entityManager.persist(new Organization("Acme"));
        joinWithRole(jane, acme, OrganizationMemberType.MEMBER);
        acme.deactivate();
        sync();

        List<UserOrganizationMembershipDTO> rows =
                userRepository.findOrganizationMembershipsForUser(jane.getId());

        assertEquals(1, rows.size());
        assertFalse(rows.get(0).organizationActive());
    }

    @Test
    void organizationMembershipsExcludeOtherUsers() {
        User jane = persist("detail-aaa@enerscope.org", PlatformRole.USER, true);
        User joe = persist("detail-bbb@enerscope.org", PlatformRole.USER, true);
        Organization acme = entityManager.persist(new Organization("Acme"));
        joinWithRole(joe, acme, OrganizationMemberType.MEMBER);
        sync();

        assertTrue(userRepository.findOrganizationMembershipsForUser(jane.getId()).isEmpty());
    }

    @Test
    void organizationMembershipsAreEmptyForAUserInNoOrganization() {
        User jane = persist("detail-orphan@enerscope.org", PlatformRole.USER, true);
        sync();

        assertTrue(userRepository.findOrganizationMembershipsForUser(jane.getId()).isEmpty());
    }

    @Test
    void projectMembershipsCarryTheProjectAndTheOrganization() {
        User jane = persist("detail-project-jane@enerscope.org", PlatformRole.USER, true);
        Organization acme = entityManager.persist(new Organization("Acme"));
        Project project = persistProject("Grid Expansion", acme);
        attachToProject(jane, project, ProjectMemberType.EDITOR);
        sync();

        List<UserProjectMembershipDTO> rows = userRepository.findProjectMembershipsForUser(jane.getId());

        assertEquals(1, rows.size());
        assertEquals("Grid Expansion", rows.get(0).projectName());
        assertEquals("Acme", rows.get(0).organizationName());
        assertEquals(ProjectMemberType.EDITOR, rows.get(0).memberType());
        assertTrue(rows.get(0).organizationActive());
    }

    @Test
    void projectMembershipsExcludeADeactivatedProject() {
        User jane = persist("detail-inactive-project@enerscope.org", PlatformRole.USER, true);
        Organization acme = entityManager.persist(new Organization("Acme"));
        Project project = persistProject("Grid Expansion", acme);
        attachToProject(jane, project, ProjectMemberType.EDITOR);
        project.deactivate();
        sync();

        assertTrue(userRepository.findProjectMembershipsForUser(jane.getId()).isEmpty());
    }

    @Test
    void projectMembershipsIncludeAProjectOfASuspendedOrganizationMarked() {
        User jane = persist("detail-project-suspended-org@enerscope.org", PlatformRole.USER, true);
        Organization acme = entityManager.persist(new Organization("Acme"));
        Project project = persistProject("Grid Expansion", acme);
        attachToProject(jane, project, ProjectMemberType.ADMIN);
        acme.deactivate();
        sync();

        List<UserProjectMembershipDTO> rows = userRepository.findProjectMembershipsForUser(jane.getId());

        assertEquals(1, rows.size());
        assertFalse(rows.get(0).organizationActive());
    }

    @Test
    void projectMembershipsExcludeOtherUsers() {
        User jane = persist("detail-project-aaa@enerscope.org", PlatformRole.USER, true);
        User joe = persist("detail-project-bbb@enerscope.org", PlatformRole.USER, true);
        Organization acme = entityManager.persist(new Organization("Acme"));
        Project project = persistProject("Grid Expansion", acme);
        attachToProject(joe, project, ProjectMemberType.EDITOR);
        sync();

        assertTrue(userRepository.findProjectMembershipsForUser(jane.getId()).isEmpty());
    }

    @Test
    void projectMembershipsAreEmptyForAUserInNoProject() {
        User jane = persist("detail-project-orphan@enerscope.org", PlatformRole.USER, true);
        sync();

        assertTrue(userRepository.findProjectMembershipsForUser(jane.getId()).isEmpty());
    }

}
