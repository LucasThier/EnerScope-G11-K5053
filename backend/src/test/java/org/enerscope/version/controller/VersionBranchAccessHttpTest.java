package org.enerscope.version.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.enums.VersionMemberPermission;
import org.enerscope.version.model.enums.VersionMemberType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class VersionBranchAccessHttpTest {

    private static final String VERTICAL_DENIED = "You are not allowed to edit this vertical's nodes in this version";
    private static final String EDIT_DENIED = "You are not allowed to edit this version";
    private static final String MANAGE_DENIED = "You are not allowed to manage this version";
    private static final String ROLES_DENIED = "You are not allowed to manage the roles of this version";
    private static final Map<VerticalEnum, NodeTypeEnum> TYPE_OF = new EnumMap<>(Map.of(
            VerticalEnum.EXTRACTION, NodeTypeEnum.WELL,
            VerticalEnum.TRANSPORTATION, NodeTypeEnum.PIPELINE,
            VerticalEnum.LIQUEFACTION, NodeTypeEnum.FLNG_UNIT,
            VerticalEnum.EXPORT, NodeTypeEnum.LNG_CAMER,
            VerticalEnum.INTERNAL_CONSUMPTION, NodeTypeEnum.INTERNAL_CONSUMPTION));

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;

    private AccessHttpFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new AccessHttpFixture(em, jwt);
    }

    @ParameterizedTest
    @EnumSource(VerticalEnum.class)
    void editorOfAVerticalCreatesAChildBecomesItsAdminAndStaysNarrowInTheParent(VerticalEnum vertical) throws Exception {
        UUID own = fixture.addNode(TYPE_OF.get(vertical));
        UUID foreign = fixture.addNode(TYPE_OF.get(anotherVerticalThan(vertical)));
        User editor = fixture.versionEditorOf(vertical);

        UUID child = createChild(editor);

        rename(editor, child, childNode(child, own)).andExpect(status().isOk());
        rename(editor, child, childNode(child, foreign)).andExpect(status().isOk());
        expectForbidden(rename(editor, fixture.version().getId(), foreign), VERTICAL_DENIED);
    }

    @Test
    void theCreatorKeepsItsCopiedRolesAndGainsAdminOnTheSameMembershipRow() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.EXPORT);

        UUID child = createChild(editor);

        List<VersionMember> members = versionMembersOf(child);
        assertEquals(1, members.stream().filter(member -> member.getUser().getId().equals(editor.getId())).count());
        Set<String> roles = rolesOf(members, editor);
        assertEquals(2, roles.size());
        assertTrue(roles.stream().anyMatch(role -> role.startsWith("EDITOR/EXPORT/")));
        assertTrue(roles.stream().anyMatch(role -> role.startsWith("ADMIN/null/")));
    }

    @Test
    void aCreatorWhoIsAlreadyAdminOfTheParentDoesNotGetASecondAdminRole() throws Exception {
        User admin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);

        UUID child = createChild(admin);

        assertEquals(1, rolesOf(versionMembersOf(child), admin).size());
    }

    @Test
    void theAdminOfTheCreatedChildCanDeleteIt() throws Exception {
        User editor = fixture.projectMember(ProjectMemberType.EDITOR);

        UUID child = createChild(editor);

        perform(editor, delete("/version/" + child)).andExpect(status().isOk());
    }

    @Test
    void aProjectAdminOrPlatformAdminCreatingAChildGetsNoVersionRole() throws Exception {
        User projectAdmin = fixture.projectMember(ProjectMemberType.ADMIN);
        User platformAdmin = fixture.user(PlatformRole.ADMIN);

        UUID byProjectAdmin = createChild(projectAdmin);
        UUID byPlatformAdmin = createChild(platformAdmin);

        assertTrue(versionMembersOf(byProjectAdmin).isEmpty());
        assertTrue(versionMembersOf(byPlatformAdmin).isEmpty());
    }

    @Test
    void projectAdminWithANarrowVersionRoleStillEditsEveryVerticalOfThatVersion() throws Exception {
        Map<VerticalEnum, UUID> nodes = new EnumMap<>(VerticalEnum.class);
        for (VerticalEnum vertical : VerticalEnum.values()) {
            nodes.put(vertical, fixture.addNode(TYPE_OF.get(vertical)));
        }
        User projectAdmin = fixture.projectMember(ProjectMemberType.ADMIN);
        fixture.grantVersionRole(projectAdmin, fixture.version(), VersionMemberType.SIMULATOR, null,
                VersionMember.defaultPermissionsFor(VersionMemberType.SIMULATOR));

        for (UUID node : nodes.values()) {
            rename(projectAdmin, fixture.version().getId(), node).andExpect(status().isOk());
        }
    }

    @Test
    void projectAdminWithANarrowVersionRoleStillDeletesAndGrantsRolesOnThatVersion() throws Exception {
        User projectAdmin = fixture.projectMember(ProjectMemberType.ADMIN);
        User platformAdmin = fixture.user(PlatformRole.ADMIN);
        UUID child = createChild(platformAdmin);
        fixture.grantVersionRole(projectAdmin, em.find(Version.class, child), VersionMemberType.SIMULATOR, null,
                VersionMember.defaultPermissionsFor(VersionMemberType.SIMULATOR));
        User colleague = fixture.projectMember(ProjectMemberType.EDITOR);

        perform(projectAdmin, post("/version/" + child + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + colleague.getId() + "\",\"memberType\":\"SIMULATOR\"}"))
                .andExpect(status().isCreated());
        perform(projectAdmin, delete("/version/" + child)).andExpect(status().isOk());
    }

    @Test
    void aVersionAdminWhoIsNotAProjectAdminCannotManageRolesButStillAdministersTheVersion() throws Exception {
        User versionAdmin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        User colleague = fixture.projectMember(ProjectMemberType.EDITOR);
        UUID version = fixture.version().getId();
        String grant = "{\"userId\":\"" + colleague.getId() + "\",\"memberType\":\"SIMULATOR\"}";

        expectForbidden(perform(versionAdmin, post("/version/" + version + "/members")
                .contentType(MediaType.APPLICATION_JSON).content(grant)), ROLES_DENIED);
        expectForbidden(perform(versionAdmin, get("/version/" + version + "/members")), ROLES_DENIED);
        expectForbidden(perform(versionAdmin, get("/version/" + version + "/member-candidates")), ROLES_DENIED);
        expectForbidden(perform(versionAdmin, delete("/version/" + version + "/members/" + UUID.randomUUID()
                + "/roles/" + UUID.randomUUID())), ROLES_DENIED);
        UUID node = fixture.addNode(TYPE_OF.get(VerticalEnum.EXTRACTION));
        rename(versionAdmin, version, node).andExpect(status().isOk());
    }

    @Test
    void aProjectAdminAndAPlatformAdminListAndGrantRolesOfAVersionWithNoRolesOfTheirOwn() throws Exception {
        User projectAdmin = fixture.projectMember(ProjectMemberType.ADMIN);
        User platformAdmin = fixture.user(PlatformRole.ADMIN);
        User colleague = fixture.projectMember(ProjectMemberType.EDITOR);
        UUID version = fixture.version().getId();

        perform(projectAdmin, get("/version/" + version + "/members")).andExpect(status().isOk());
        perform(projectAdmin, get("/version/" + version + "/member-candidates")).andExpect(status().isOk());
        perform(platformAdmin, get("/version/" + version + "/members")).andExpect(status().isOk());
        perform(projectAdmin, post("/version/" + version + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + colleague.getId() + "\",\"memberType\":\"SIMULATOR\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void simulatorOfTheParentCannotCreateAChild() throws Exception {
        User simulator = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        long before = versionCount();

        expectForbidden(createChildRequest(simulator, fixture.version().getId()), EDIT_DENIED);

        assertEquals(before, versionCount());
    }

    @Test
    void versionAdminCreatesAChildAndEditsEveryVerticalInIt() throws Exception {
        assertCreatesAChildAndEditsEveryVertical(fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN));
    }

    @Test
    void projectAdminCreatesAChildAndEditsEveryVerticalInIt() throws Exception {
        assertCreatesAChildAndEditsEveryVertical(fixture.projectMember(ProjectMemberType.ADMIN));
    }

    @Test
    void platformAdminCreatesAChildAndEditsEveryVerticalInIt() throws Exception {
        assertCreatesAChildAndEditsEveryVertical(fixture.user(PlatformRole.ADMIN));
    }

    @Test
    void projectEditorWithoutVersionRoleBecomesAdminOfTheChildOnly() throws Exception {
        User editor = fixture.projectMember(ProjectMemberType.EDITOR);

        UUID child = assertCreatesAChildAndEditsEveryVertical(editor);

        assertEquals(Set.of("ADMIN/null/ADMIN/"
                        + new java.util.TreeSet<>(VersionMember.defaultPermissionsFor(VersionMemberType.ADMIN))),
                rolesOf(versionMembersOf(child), editor));
        assertTrue(versionMembersOf(fixture.version().getId()).stream()
                .noneMatch(member -> member.getUser().getId().equals(editor.getId())));
    }

    @Test
    void everyRoleOfTheParentIsCopiedWithItsVerticalAndPermissions() throws Exception {
        User exportEditor = fixture.versionEditorOf(VerticalEnum.EXPORT);
        User simulator = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        User admin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        User narrowAdmin = fixture.projectMember(ProjectMemberType.EDITOR);
        fixture.grantVersionRole(narrowAdmin, fixture.version(), VersionMemberType.ADMIN, null,
                Set.of(VersionMemberPermission.MANAGE_VERSION, VersionMemberPermission.VIEW_VERSION));

        UUID child = createChild(fixture.user(PlatformRole.ADMIN));

        List<VersionMember> parentMembers = versionMembersOf(fixture.version().getId());
        List<VersionMember> childMembers = versionMembersOf(child);
        assertEquals(4, childMembers.size());
        for (User user : List.of(exportEditor, simulator, admin, narrowAdmin)) {
            assertEquals(rolesOf(parentMembers, user), rolesOf(childMembers, user));
        }
    }

    @Test
    void aVersionRoleOfSomeoneOutsideTheProjectIsNotCopied() throws Exception {
        User outsider = fixture.organizationMember(org.enerscope.organization.model.enums.OrganizationMemberType.MEMBER);
        fixture.grantVersionRole(outsider, fixture.version(), VersionMemberType.ADMIN, null,
                VersionMember.defaultPermissionsFor(VersionMemberType.ADMIN));

        UUID child = createChild(fixture.user(PlatformRole.ADMIN));

        assertTrue(versionMembersOf(child).stream().noneMatch(member -> member.getUser().getId().equals(outsider.getId())));
    }

    @Test
    void aVersionRoleOfSomeoneRemovedFromTheProjectIsNotCopied() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.EXTRACTION);
        User platformAdmin = fixture.user(PlatformRole.ADMIN);
        UUID memberId = projectMemberOf(editor).getId();
        perform(platformAdmin, delete("/projects/" + fixture.project().getId() + "/members/" + memberId))
                .andExpect(status().isOk());

        UUID child = createChild(platformAdmin);

        assertTrue(versionMembersOf(child).isEmpty());
    }

    @Test
    void aVersionRoleOfADeactivatedProjectMemberIsCopiedSoItStaysNarrow() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.EXPORT);
        projectMemberOf(editor).deactivate();
        em.flush();

        UUID child = createChild(fixture.user(PlatformRole.ADMIN));

        assertEquals(rolesOf(versionMembersOf(fixture.version().getId()), editor), rolesOf(versionMembersOf(child), editor));
    }

    @Test
    void aParentFromAnotherProjectAnswers403ToSomeoneWithoutAccessToIt() throws Exception {
        Project foreign = fixture.foreignProject();
        User projectAdmin = fixture.projectMember(ProjectMemberType.ADMIN);
        long before = versionCount();

        createChildRequest(projectAdmin, foreign.getVersions().getFirst().getId())
                .andExpect(status().isForbidden());

        assertEquals(before, versionCount());
    }

    @Test
    void aParentFromAnotherProjectAnswers400ToSomeoneWhoCanEditIt() throws Exception {
        Project foreign = fixture.foreignProject();
        User both = fixture.projectMember(ProjectMemberType.ADMIN);
        fixture.joinProject(both, foreign, ProjectMemberType.ADMIN);
        long before = versionCount();

        createChildRequest(both, foreign.getVersions().getFirst().getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parent version must belong to the selected project"));

        assertEquals(before, versionCount());
    }

    @Test
    void anUnknownParentAnswers403ToAProjectEditor() throws Exception {
        createChildRequest(fixture.projectMember(ProjectMemberType.EDITOR), UUID.randomUUID())
                .andExpect(status().isForbidden());
    }

    @Test
    void reparentingUnderAVersionOfAnotherProjectIsRejectedAndTheParentStays() throws Exception {
        Project foreign = fixture.foreignProject();
        UUID foreignVersion = foreign.getVersions().getFirst().getId();
        User platformAdmin = fixture.user(PlatformRole.ADMIN);
        UUID child = createChild(platformAdmin);

        reparent(platformAdmin, child, foreignVersion)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parent version must belong to the same project"));
        reparent(fixture.projectMember(ProjectMemberType.ADMIN), child, foreignVersion)
                .andExpect(status().isForbidden());

        assertEquals(fixture.version().getId(), parentOf(child));
    }

    @Test
    void reparentingAVersionUnderItsOwnDescendantIsRejected() throws Exception {
        User platformAdmin = fixture.user(PlatformRole.ADMIN);
        UUID child = createChild(platformAdmin);

        reparent(platformAdmin, fixture.version().getId(), child)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A version cannot be its own ancestor"));

        assertNull(parentOf(fixture.version().getId()));
    }

    @Test
    void anEditorOfTheVersionCannotReparentIt() throws Exception {
        User platformAdmin = fixture.user(PlatformRole.ADMIN);
        UUID first = createChild(platformAdmin);
        UUID second = createChild(platformAdmin);
        User editor = fixture.projectMember(ProjectMemberType.EDITOR);
        fixture.grantVersionRole(editor, em.find(Version.class, second), VersionMemberType.EDITOR, VerticalEnum.EXTRACTION,
                VersionMember.defaultPermissionsFor(VersionMemberType.EDITOR));

        expectForbidden(reparent(editor, second, first), MANAGE_DENIED);
        reparent(fixture.projectMember(ProjectMemberType.EDITOR), second, first).andExpect(status().isForbidden());

        assertEquals(fixture.version().getId(), parentOf(second));
    }

    @Test
    void aProjectAdminReparentsWithinTheProject() throws Exception {
        User platformAdmin = fixture.user(PlatformRole.ADMIN);
        UUID first = createChild(platformAdmin);
        UUID second = createChild(platformAdmin);

        reparent(fixture.projectMember(ProjectMemberType.ADMIN), second, first).andExpect(status().isOk());

        assertEquals(first, parentOf(second));
    }

    private UUID assertCreatesAChildAndEditsEveryVertical(User user) throws Exception {
        Map<VerticalEnum, UUID> nodes = new EnumMap<>(VerticalEnum.class);
        for (VerticalEnum vertical : VerticalEnum.values()) {
            nodes.put(vertical, fixture.addNode(TYPE_OF.get(vertical)));
        }
        UUID child = createChild(user);
        for (UUID node : nodes.values()) {
            rename(user, child, childNode(child, node)).andExpect(status().isOk());
        }
        return child;
    }

    private UUID createChild(User user) throws Exception {
        String body = createChildRequest(user, fixture.version().getId())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private ResultActions createChildRequest(User user, UUID parent) throws Exception {
        return perform(user, post("/projects/" + fixture.project().getId() + "/version")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Child " + UUID.randomUUID() + "\",\"parentVersion\":\"" + parent + "\"}"));
    }

    private ResultActions rename(User user, UUID version, UUID node) throws Exception {
        return perform(user, patch("/version/" + version + "/node/" + node + "/basics")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed " + UUID.randomUUID() + "\"}"));
    }

    private ResultActions reparent(User user, UUID version, UUID parent) throws Exception {
        return perform(user, patch("/version/" + version)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentVersion\":\"" + parent + "\"}"));
    }

    private UUID childNode(UUID child, UUID parentNode) {
        em.flush();
        UUID identity = em.find(BaseNode.class, parentNode).getIdentityId();
        return em.find(Version.class, child).getNodeSnapshot().stream()
                .filter(node -> identity.equals(node.getIdentityId()))
                .findFirst().orElseThrow().getId();
    }

    private UUID parentOf(UUID version) {
        em.flush();
        em.clear();
        Version parent = em.find(Version.class, version).getParentVersion();
        return parent == null ? null : parent.getId();
    }

    private List<VersionMember> versionMembersOf(UUID version) {
        em.flush();
        return em.createQuery("select m from VersionMember m where m.version.id = :version", VersionMember.class)
                .setParameter("version", version)
                .getResultList();
    }

    private static Set<String> rolesOf(List<VersionMember> members, User user) {
        VersionMember member = members.stream()
                .filter(candidate -> candidate.getUser().getId().equals(user.getId()))
                .findFirst().orElseThrow();
        return member.getRoles().stream()
                .map(role -> role.getMemberType() + "/" + role.getVertical() + "/" + role.getName() + "/"
                        + new java.util.TreeSet<>(role.getPermissions()))
                .collect(java.util.stream.Collectors.toSet());
    }

    private ProjectMember projectMemberOf(User user) {
        return em.createQuery("select m from ProjectMember m where m.user.id = :user and m.project.id = :project",
                        ProjectMember.class)
                .setParameter("user", user.getId())
                .setParameter("project", fixture.project().getId())
                .getSingleResult();
    }

    private long versionCount() {
        em.flush();
        return em.createQuery("select count(v) from Version v", Long.class).getSingleResult();
    }

    private void expectForbidden(ResultActions result, String message) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message));
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.bearer(user)));
    }

    private static VerticalEnum anotherVerticalThan(VerticalEnum vertical) {
        return vertical == VerticalEnum.EXTRACTION ? VerticalEnum.EXPORT : VerticalEnum.EXTRACTION;
    }
}
