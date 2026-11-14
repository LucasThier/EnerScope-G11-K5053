package org.enerscope.version.controller;

import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class VersionRoleAfterProjectRemovalHttpTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;

    private AccessHttpFixture fixture;
    private User platformAdmin;

    @BeforeEach
    void setUp() {
        fixture = new AccessHttpFixture(em, jwt);
        platformAdmin = fixture.user(PlatformRole.ADMIN);
    }

    @Test
    void removingAVersionAdminFromTheProjectDeletesTheirVersionRolesAndRevokesAccess() throws Exception {
        User versionAdmin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        mvc.perform(get(candidatesPath()).header("Authorization", fixture.bearer(versionAdmin)))
                .andExpect(status().isOk());

        mvc.perform(delete("/projects/" + fixture.project().getId() + "/members/" + projectMemberId(versionAdmin))
                        .header("Authorization", fixture.bearer(platformAdmin)))
                .andExpect(status().isOk());

        assertEquals(0L, versionMemberRows(versionAdmin));
        assertHasNoAccess(versionAdmin);
    }

    @Test
    void removingSomeoneFromTheOrganizationDeletesTheirVersionRolesAndRevokesAccess() throws Exception {
        User versionAdmin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        em.clear();

        mvc.perform(delete("/organizations/" + fixture.project().getOrganization().getId() + "/members/"
                        + organizationMemberId(versionAdmin))
                        .header("Authorization", fixture.bearer(platformAdmin)))
                .andExpect(status().isOk());

        assertEquals(0L, versionMemberRows(versionAdmin));
        assertHasNoAccess(versionAdmin);
    }

    @Test
    void removingOneMemberKeepsTheVersionRolesOfTheOthers() throws Exception {
        User leaving = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        User staying = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);

        mvc.perform(delete("/projects/" + fixture.project().getId() + "/members/" + projectMemberId(leaving))
                        .header("Authorization", fixture.bearer(platformAdmin)))
                .andExpect(status().isOk());

        assertEquals(1L, versionMemberRows(staying));
        mvc.perform(get(candidatesPath()).header("Authorization", fixture.bearer(staying)))
                .andExpect(status().isOk());
    }

    @Test
    void aPreexistingOrphanRoleRowGrantsNoAccessAndIsNotListed() throws Exception {
        User orphan = fixture.organizationMember(OrganizationMemberType.MEMBER);
        grantDirectly(orphan, VersionMemberType.ADMIN);

        assertEquals(1L, versionMemberRows(orphan));
        assertHasNoAccess(orphan);
        mvc.perform(get("/version/" + fixture.version().getId() + "/members")
                        .header("Authorization", fixture.bearer(platformAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].userId", not(hasItem(orphan.getId().toString()))));
    }

    @Test
    void aPlatformAdminWithAnOrphanRoleRowKeepsFullAccess() throws Exception {
        User admin = fixture.user(PlatformRole.ADMIN);
        grantDirectly(admin, VersionMemberType.SIMULATOR);

        mvc.perform(get(diagramPath()).header("Authorization", fixture.bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get(candidatesPath()).header("Authorization", fixture.bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void reAddingARemovedMemberDoesNotGiveBackTheirOldVersionRoles() throws Exception {
        User versionAdmin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        mvc.perform(delete("/projects/" + fixture.project().getId() + "/members/" + projectMemberId(versionAdmin))
                        .header("Authorization", fixture.bearer(platformAdmin)))
                .andExpect(status().isOk());

        addToProject(versionAdmin);

        assertEquals(0L, versionMemberRows(versionAdmin));
        assertFallsBackToTheProjectEditorRole(versionAdmin);
    }

    @Test
    void reAddingAMemberPurgesAPreexistingOrphanRoleRow() throws Exception {
        User orphan = fixture.organizationMember(OrganizationMemberType.MEMBER);
        grantDirectly(orphan, VersionMemberType.ADMIN);

        addToProject(orphan);

        assertEquals(0L, versionMemberRows(orphan));
        assertFallsBackToTheProjectEditorRole(orphan);
    }

    private void assertHasNoAccess(User user) throws Exception {
        String bearer = fixture.bearer(user);
        mvc.perform(get(diagramPath()).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(get(candidatesPath()).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/version/" + fixture.version().getId() + "/members")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.getId() + "\",\"memberType\":\"SIMULATOR\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/projects/" + fixture.project().getId() + "/access").header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    private void assertFallsBackToTheProjectEditorRole(User user) throws Exception {
        String bearer = fixture.bearer(user);
        mvc.perform(get("/projects/" + fixture.project().getId() + "/access").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versions[0].source").value("PROJECT_FALLBACK"))
                .andExpect(jsonPath("$.data.versions[0].roles.length()").value(0))
                .andExpect(jsonPath("$.data.versions[0].canManage").value(false));
        mvc.perform(get(diagramPath()).header("Authorization", bearer))
                .andExpect(status().isOk());
        mvc.perform(get(candidatesPath()).header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    private void addToProject(User user) throws Exception {
        mvc.perform(post("/projects/" + fixture.project().getId() + "/members")
                        .header("Authorization", fixture.bearer(platformAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.getId() + "\",\"memberType\":\"EDITOR\"}"))
                .andExpect(status().isCreated());
    }

    private void grantDirectly(User user, VersionMemberType type) {
        VersionMember member = new VersionMember(user, fixture.version());
        member.addRole(new VersionMemberRole(type.name(), type,
                EnumSet.copyOf(VersionMember.defaultPermissionsFor(type)), null));
        em.persist(member);
        em.flush();
    }

    private long versionMemberRows(User user) {
        return em.createQuery("SELECT COUNT(m) FROM VersionMember m WHERE m.user.id = :userId", Long.class)
                .setParameter("userId", user.getId())
                .getSingleResult();
    }

    private UUID projectMemberId(User user) {
        return em.createQuery("SELECT m.id FROM ProjectMember m WHERE m.user.id = :userId AND m.project.id = :projectId",
                        UUID.class)
                .setParameter("userId", user.getId())
                .setParameter("projectId", fixture.project().getId())
                .getSingleResult();
    }

    private UUID organizationMemberId(User user) {
        return em.createQuery("SELECT m.id FROM OrganizationMember m WHERE m.user.id = :userId", UUID.class)
                .setParameter("userId", user.getId())
                .getSingleResult();
    }

    private String diagramPath() {
        return "/version/" + fixture.version().getId() + "/diagram";
    }

    private String candidatesPath() {
        return "/version/" + fixture.version().getId() + "/member-candidates";
    }
}
