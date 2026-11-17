package org.enerscope.version.controller;

import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberPermission;
import org.enerscope.version.model.enums.VersionMemberType;
import org.enerscope.version.repository.VersionMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code PUT /version/{id}/members/{memberId}/roles/{roleId}}: who may change a role and what it keeps. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class VersionRoleChangeHttpTest {

    private static final String ROLES_DENIED = "You are not allowed to manage the roles of this version";

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;
    @Autowired
    VersionMemberRepository members;

    private AccessHttpFixture fixture;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        fixture = new AccessHttpFixture(em, jwt);
        versionId = fixture.version().getId();
    }

    @Test
    void projectAdminTurnsAnEditorIntoASimulatorAndTheRoleKeepsItsId() throws Exception {
        User target = fixture.projectEditorWithVersionRoles(VersionMemberType.EDITOR);
        VersionMember member = memberOf(target, versionId);
        UUID roleId = onlyRole(member).getId();

        change(fixture.projectMember(ProjectMemberType.ADMIN), versionId, member.getId(), roleId, "SIMULATOR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(1))
                .andExpect(jsonPath("$.data.roles[0].roleId").value(roleId.toString()))
                .andExpect(jsonPath("$.data.roles[0].memberType").value("SIMULATOR"));

        em.flush();
        em.clear();
        VersionMemberRole stored = onlyRole(memberOf(target, versionId));
        assertEquals(roleId, stored.getId());
        assertEquals(VersionMemberType.SIMULATOR, stored.getMemberType());
        assertEquals(VersionMember.defaultPermissionsFor(VersionMemberType.SIMULATOR), stored.getPermissions());
    }

    @Test
    void platformAdminChangesARoleAndNoPermissionOfTheOldTypeSurvives() throws Exception {
        User target = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        VersionMember member = memberOf(target, versionId);
        UUID roleId = onlyRole(member).getId();

        change(fixture.user(PlatformRole.ADMIN), versionId, member.getId(), roleId, "EDITOR", "EXPORT")
                .andExpect(status().isOk());

        em.flush();
        em.clear();
        VersionMemberRole stored = onlyRole(memberOf(target, versionId));
        assertEquals(VerticalEnum.EXPORT, stored.getVertical());
        Set<VersionMemberPermission> permissions = stored.getPermissions();
        assertEquals(VersionMember.defaultPermissionsFor(VersionMemberType.EDITOR), permissions);
        assertTrue(permissions.stream().noneMatch(p -> p == VersionMemberPermission.MANAGE_VERSION));
    }

    @Test
    void aMemberWithASingleRoleIsNotDeletedWhenItsRoleChanges() throws Exception {
        User target = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        VersionMember member = memberOf(target, versionId);
        UUID memberId = member.getId();
        UUID roleId = onlyRole(member).getId();

        change(fixture.projectMember(ProjectMemberType.ADMIN), versionId, memberId, roleId, "ADMIN", null)
                .andExpect(status().isOk());

        em.flush();
        em.clear();
        VersionMember stored = memberOf(target, versionId);
        assertEquals(memberId, stored.getId());
        assertEquals(roleId, onlyRole(stored).getId());
    }

    @Test
    void changingToARoleTheMemberAlreadyHoldsGets400LikeGrantingItAgain() throws Exception {
        User target = fixture.projectEditorWithVersionRoles(VersionMemberType.EDITOR, VersionMemberType.SIMULATOR);
        VersionMember member = memberOf(target, versionId);
        UUID editorRole = roleOfType(member, VersionMemberType.EDITOR).getId();
        User projectAdmin = fixture.projectMember(ProjectMemberType.ADMIN);

        change(projectAdmin, versionId, member.getId(), editorRole, "SIMULATOR", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User already has this role"));
        mvc.perform(post("/version/" + versionId + "/members").header("Authorization", fixture.bearer(projectAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + target.getId() + "\",\"memberType\":\"SIMULATOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User already has this role"));

        em.flush();
        em.clear();
        assertEquals(VersionMemberType.EDITOR,
                roleOfId(memberOf(target, versionId), editorRole).getMemberType());
    }

    @Test
    void onlyProjectAdminAndPlatformAdminMayChangeARole() throws Exception {
        User target = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        VersionMember member = memberOf(target, versionId);
        UUID roleId = onlyRole(member).getId();
        User[] refused = {
                fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN),
                fixture.projectEditorWithVersionRoles(VersionMemberType.EDITOR),
                fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR),
                fixture.projectMember(ProjectMemberType.EDITOR),
                fixture.user(PlatformRole.USER)};

        for (User caller : refused) {
            change(caller, versionId, member.getId(), roleId, "ADMIN", null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(ROLES_DENIED));
        }

        em.flush();
        em.clear();
        assertEquals(VersionMemberType.SIMULATOR, onlyRole(memberOf(target, versionId)).getMemberType());
    }

    @Test
    void aCallerWithoutPermissionGets403EvenForAnUnknownMemberOrRole() throws Exception {
        User versionAdmin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);

        change(versionAdmin, versionId, UUID.randomUUID(), UUID.randomUUID(), "ADMIN", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ROLES_DENIED));
    }

    @Test
    void anUnknownMemberOrRoleAnswers404ToAProjectAdmin() throws Exception {
        User target = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        VersionMember member = memberOf(target, versionId);
        User projectAdmin = fixture.projectMember(ProjectMemberType.ADMIN);

        change(projectAdmin, versionId, UUID.randomUUID(), onlyRole(member).getId(), "ADMIN", null)
                .andExpect(status().isNotFound());
        change(projectAdmin, versionId, member.getId(), UUID.randomUUID(), "ADMIN", null)
                .andExpect(status().isNotFound());
    }

    @Test
    void aMemberOrRoleOfAnotherVersionAnswers404() throws Exception {
        Version other = fixture.detachedVersion();
        User foreign = fixture.projectMember(ProjectMemberType.EDITOR);
        fixture.grantVersionRole(foreign, other, VersionMemberType.SIMULATOR, null,
                VersionMember.defaultPermissionsFor(VersionMemberType.SIMULATOR));
        VersionMember foreignMember = memberOf(foreign, other.getId());
        User local = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        VersionMember localMember = memberOf(local, versionId);
        User platformAdmin = fixture.user(PlatformRole.ADMIN);

        change(platformAdmin, versionId, foreignMember.getId(), onlyRole(foreignMember).getId(), "ADMIN", null)
                .andExpect(status().isNotFound());
        change(platformAdmin, versionId, localMember.getId(), onlyRole(foreignMember).getId(), "ADMIN", null)
                .andExpect(status().isNotFound());
        assertEquals(VersionMemberType.SIMULATOR, onlyRole(foreignMember).getMemberType());
    }

    @Test
    void anUnknownVersionAnswers404ToAPlatformAdminAnd403ToAnyoneElse() throws Exception {
        UUID unknown = UUID.randomUUID();

        change(fixture.user(PlatformRole.ADMIN), unknown, UUID.randomUUID(), UUID.randomUUID(), "ADMIN", null)
                .andExpect(status().isNotFound());
        change(fixture.projectMember(ProjectMemberType.ADMIN), unknown, UUID.randomUUID(), UUID.randomUUID(),
                "ADMIN", null).andExpect(status().isForbidden());
    }

    @Test
    void aBodyThatBreaksTheRoleShapeGets400() throws Exception {
        User target = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        VersionMember member = memberOf(target, versionId);
        User projectAdmin = fixture.projectMember(ProjectMemberType.ADMIN);
        UUID roleId = onlyRole(member).getId();

        change(projectAdmin, versionId, member.getId(), roleId, "EDITOR", null).andExpect(status().isBadRequest());
        change(projectAdmin, versionId, member.getId(), roleId, "ADMIN", "EXPORT").andExpect(status().isBadRequest());
        mvc.perform(put(path(versionId, member.getId(), roleId)).header("Authorization", fixture.bearer(projectAdmin))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions change(User caller, UUID version, UUID memberId, UUID roleId, String type, String vertical)
            throws Exception {
        MockHttpServletRequestBuilder request = put(path(version, memberId, roleId))
                .header("Authorization", fixture.bearer(caller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\":\"" + type + "\""
                        + (vertical == null ? "" : ",\"vertical\":\"" + vertical + "\"") + "}");
        return mvc.perform(request);
    }

    private static String path(UUID version, UUID memberId, UUID roleId) {
        return "/version/" + version + "/members/" + memberId + "/roles/" + roleId;
    }

    private VersionMember memberOf(User user, UUID version) {
        return members.findByVersionIdAndUserId(version, user.getId()).orElseThrow();
    }

    private static VersionMemberRole onlyRole(VersionMember member) {
        assertEquals(1, member.getRoles().size());
        return member.getRoles().iterator().next();
    }

    private static VersionMemberRole roleOfType(VersionMember member, VersionMemberType type) {
        return member.getRoles().stream().filter(r -> r.getMemberType() == type).findFirst().orElseThrow();
    }

    private static VersionMemberRole roleOfId(VersionMember member, UUID id) {
        return member.getRoles().stream().filter(r -> r.getId().equals(id)).collect(Collectors.toList()).get(0);
    }
}
