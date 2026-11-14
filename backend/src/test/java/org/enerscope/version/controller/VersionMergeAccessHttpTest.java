package org.enerscope.version.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.ConflictEntityType;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionConflict;
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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class VersionMergeAccessHttpTest {

    private static final String VERTICAL_DENIED = "You are not allowed to edit this vertical's nodes in this version";
    private static final String EDIT_DENIED = "You are not allowed to edit this version";
    private static final String MANAGE_DENIED = "You are not allowed to manage this version";
    private static final String DIFFERENT_PROJECTS = "The versions belong to different projects";
    private static final Set<VersionMemberPermission> MANAGE_WITHOUT_EDIT =
            Set.of(VersionMemberPermission.MANAGE_VERSION, VersionMemberPermission.VIEW_VERSION);
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
    private User platformAdmin;

    @BeforeEach
    void setUp() {
        fixture = new AccessHttpFixture(em, jwt);
        platformAdmin = fixture.user(PlatformRole.ADMIN);
    }

    @ParameterizedTest
    @EnumSource(VerticalEnum.class)
    void adminOfTheDestinationMergesAChangeOfAnyVertical(VerticalEnum vertical) throws Exception {
        User admin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        UUID identity = identityOf(fixture.addNode(TYPE_OF.get(vertical)));
        UUID child = childThatDeletes(identity);

        merge(admin, child).andExpect(status().isOk());

        assertFalse(parentHolds(identity));
    }

    @ParameterizedTest
    @EnumSource(VerticalEnum.class)
    void editorOfTheChangedVerticalCannotMergeAndNothingIsMerged(VerticalEnum vertical) throws Exception {
        User editor = fixture.versionEditorOf(vertical);
        UUID identity = identityOf(fixture.addNode(TYPE_OF.get(vertical)));
        UUID child = childThatDeletes(identity);

        expectForbidden(merge(editor, child), MANAGE_DENIED);

        assertTrue(parentHolds(identity));
    }

    @Test
    void adminOnlyOfTheChildCannotMergeIntoAParentItDoesNotAdminister() throws Exception {
        UUID identity = identityOf(fixture.addNode(NodeTypeEnum.WELL));
        UUID child = childThatDeletes(identity);
        User childAdmin = fixture.projectMember(ProjectMemberType.EDITOR);
        fixture.grantVersionRole(childAdmin, em.find(Version.class, child), VersionMemberType.ADMIN, null,
                VersionMember.defaultPermissionsFor(VersionMemberType.ADMIN));

        merge(childAdmin, child).andExpect(status().isForbidden());

        assertTrue(parentHolds(identity));
    }

    @Test
    void projectAdminWithoutVersionRoleMerges() throws Exception {
        User projectAdmin = fixture.projectMember(ProjectMemberType.ADMIN);
        UUID identity = identityOf(fixture.addNode(NodeTypeEnum.LNG_CAMER));
        UUID child = childThatDeletes(identity);

        merge(projectAdmin, child).andExpect(status().isOk());

        assertFalse(parentHolds(identity));
    }

    @Test
    void platformAdminMerges() throws Exception {
        UUID identity = identityOf(fixture.addNode(NodeTypeEnum.PIPELINE));
        UUID child = childThatDeletes(identity);

        merge(platformAdmin, child).andExpect(status().isOk());

        assertFalse(parentHolds(identity));
    }

    @Test
    void adminOfTheDestinationWithoutEditPermissionCannotMergeANodeChangeAndNothingIsMerged() throws Exception {
        UUID identity = identityOf(fixture.addNode(NodeTypeEnum.FLNG_UNIT));
        UUID child = childThatDeletes(identity);
        User narrowAdmin = fixture.projectMember(ProjectMemberType.EDITOR);
        fixture.grantVersionRole(narrowAdmin, fixture.version(), VersionMemberType.ADMIN, null, MANAGE_WITHOUT_EDIT);

        expectForbidden(merge(narrowAdmin, child), VERTICAL_DENIED);

        assertTrue(parentHolds(identity));
    }

    @Test
    void adminOfTheDestinationWithoutEditPermissionCannotMergeAConnectionChange() throws Exception {
        UUID from = fixture.addNode(NodeTypeEnum.WELL);
        UUID to = fixture.addNode(NodeTypeEnum.PIPELINE);
        NodeConnection connection = new NodeConnection(UUID.randomUUID(), from, to);
        em.persist(connection);
        fixture.version().getConnectionSnapshot().add(connection);
        em.flush();
        UUID child = createChild();
        UUID childConnection = em.find(Version.class, child).getConnectionSnapshot().getFirst().getId();
        perform(platformAdmin, delete("/version/" + child + "/connection/" + childConnection)).andExpect(status().isOk());
        User narrowAdmin = fixture.projectMember(ProjectMemberType.EDITOR);
        fixture.grantVersionRole(narrowAdmin, fixture.version(), VersionMemberType.ADMIN, null, MANAGE_WITHOUT_EDIT);

        expectForbidden(merge(narrowAdmin, child), EDIT_DENIED);

        em.flush();
        em.clear();
        assertEquals(List.of(connection.getIdentityId()), em.find(Version.class, fixture.version().getId())
                .getConnectionSnapshot().stream().map(NodeConnection::getIdentityId).toList());
    }

    @Test
    void aChildHangingFromAVersionOfAnotherProjectCannotBeMergedIntoItAndNothingIsCopied() throws Exception {
        Project foreign = fixture.foreignProject();
        Version foreignParent = foreign.getVersions().getFirst();
        UUID foreignNode = foreignParent.getNodeSnapshot().getFirst().getId();
        Well smuggled = fixture.newNode(NodeTypeEnum.WELL);
        NodeChange add = new NodeChange();
        add.setChangeType(ChangeTypeEnum.ADD);
        add.setResultNode(smuggled);
        Version child = new Version("Smuggled", foreignParent, new ArrayList<>(List.of(smuggled)), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(List.of(add)));
        em.persist(child);
        fixture.project().addVersion(child);
        em.flush();
        User attacker = fixture.projectMember(ProjectMemberType.ADMIN);
        User both = fixture.projectMember(ProjectMemberType.ADMIN);
        fixture.joinProject(both, foreign, ProjectMemberType.ADMIN);

        merge(attacker, child.getId()).andExpect(status().isForbidden());
        expectDifferentProjects(merge(both, child.getId()));
        expectDifferentProjects(merge(platformAdmin, child.getId()));

        em.flush();
        em.clear();
        assertEquals(List.of(foreignNode), em.find(Version.class, foreignParent.getId())
                .getNodeSnapshot().stream().map(BaseNode::getId).toList());
        assertTrue(em.createQuery("select c from VersionConflict c where c.sourceVersion.id = :child", VersionConflict.class)
                .setParameter("child", child.getId()).getResultList().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(VerticalEnum.class)
    void adminOfTheSiblingAcceptsTheParentDataOfAnyVertical(VerticalEnum vertical) throws Exception {
        User admin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        UUID parentNode = fixture.addNode(TYPE_OF.get(vertical));
        UUID child = createChild();
        UUID conflict = conflictOnRenamedParentNode(parentNode, child);

        resolve(admin, conflict).andExpect(status().isOk());

        assertEquals("Merged name", childNodeName(child, identityOf(parentNode)));
        assertTrue(em.find(VersionConflict.class, conflict).isResolved());
    }

    @Test
    void editorOfTheNodeVerticalCannotResolveAndNothingIsCopied() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.EXPORT);
        UUID parentNode = fixture.addNode(NodeTypeEnum.LNG_CAMER);
        UUID child = createChild();
        String before = childNodeName(child, identityOf(parentNode));
        UUID conflict = conflictOnRenamedParentNode(parentNode, child);

        expectForbidden(resolve(editor, conflict), MANAGE_DENIED);

        assertEquals(before, childNodeName(child, identityOf(parentNode)));
        assertFalse(em.find(VersionConflict.class, conflict).isResolved());
    }

    @Test
    void adminOfTheSiblingWithoutEditPermissionCannotResolveANodeConflict() throws Exception {
        UUID parentNode = fixture.addNode(NodeTypeEnum.PIPELINE);
        UUID child = createChild();
        String before = childNodeName(child, identityOf(parentNode));
        UUID conflict = conflictOnRenamedParentNode(parentNode, child);
        User narrowAdmin = fixture.projectMember(ProjectMemberType.EDITOR);
        fixture.grantVersionRole(narrowAdmin, em.find(Version.class, child), VersionMemberType.ADMIN, null,
                MANAGE_WITHOUT_EDIT);

        expectForbidden(resolve(narrowAdmin, conflict), VERTICAL_DENIED);

        assertEquals(before, childNodeName(child, identityOf(parentNode)));
        assertFalse(em.find(VersionConflict.class, conflict).isResolved());
    }

    @Test
    void aChildHangingFromAVersionOfAnotherProjectCannotAcceptItsDataAndNothingIsCopied() throws Exception {
        Project foreign = fixture.foreignProject();
        Version foreignParent = foreign.getVersions().getFirst();
        BaseNode foreignNode = foreignParent.getNodeSnapshot().getFirst();
        foreignNode.setName("Foreign secret");
        Well mine = fixture.newNode(NodeTypeEnum.WELL);
        mine.setName("Mine");
        mine.setIdentityId(foreignNode.getIdentityId());
        Version child = new Version("Smuggled", foreignParent, new ArrayList<>(List.of(mine)), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());
        em.persist(child);
        fixture.project().addVersion(child);
        VersionConflict conflict = new VersionConflict(foreignParent, foreignParent, child, ConflictEntityType.NODE,
                foreignNode.getIdentityId());
        em.persist(conflict);
        em.flush();
        User attacker = fixture.projectMember(ProjectMemberType.ADMIN);
        User both = fixture.projectMember(ProjectMemberType.ADMIN);
        fixture.joinProject(both, foreign, ProjectMemberType.ADMIN);

        resolve(attacker, conflict.getId()).andExpect(status().isForbidden());
        expectDifferentProjects(resolve(both, conflict.getId()));
        expectDifferentProjects(resolve(platformAdmin, conflict.getId()));

        assertEquals("Mine", childNodeName(child.getId(), foreignNode.getIdentityId()));
        assertEquals(1, em.find(Version.class, child.getId()).getNodeSnapshot().size());
        assertFalse(em.find(VersionConflict.class, conflict.getId()).isResolved());
    }

    @Test
    void mergingIntoAParentRecordsNoConflictForAChildOfAnotherProject() throws Exception {
        Project foreign = fixture.foreignProject();
        Version foreignParent = foreign.getVersions().getFirst();
        BaseNode foreignNode = foreignParent.getNodeSnapshot().getFirst();
        Well edited = fixture.newNode(NodeTypeEnum.WELL);
        edited.setIdentityId(foreignNode.getIdentityId());
        NodeChange edit = new NodeChange();
        edit.setChangeType(ChangeTypeEnum.EDIT);
        edit.setChangedNode(foreignNode);
        edit.setResultNode(edited);
        Version source = seededChild(foreignParent, foreign, edited, List.of(edit));
        Version foreignSibling = seededChild(foreignParent, foreign, sameIdentityAs(foreignNode), List.of());
        Version smuggled = seededChild(foreignParent, fixture.project(), sameIdentityAs(foreignNode), List.of());
        User foreignAdmin = fixture.user(PlatformRole.USER);
        fixture.joinProject(foreignAdmin, foreign, ProjectMemberType.ADMIN);

        merge(foreignAdmin, source.getId()).andExpect(status().isOk());

        assertEquals(0, conflictsAgainst(smuggled.getId()));
        assertEquals(1, conflictsAgainst(foreignSibling.getId()));
    }

    @Test
    void listingAChildHidesConflictsRecordedAgainstAnotherProject() throws Exception {
        Project foreign = fixture.foreignProject();
        Version foreignParent = foreign.getVersions().getFirst();
        BaseNode foreignNode = foreignParent.getNodeSnapshot().getFirst();
        foreignNode.setName("Foreign secret");
        Version smuggled = seededChild(foreignParent, fixture.project(), sameIdentityAs(foreignNode), List.of());
        VersionConflict hidden = new VersionConflict(foreignParent, foreignParent, smuggled, ConflictEntityType.NODE,
                foreignNode.getIdentityId());
        UUID localIdentity = identityOf(fixture.nodeId());
        VersionConflict kept = new VersionConflict(fixture.version(), fixture.version(), smuggled,
                ConflictEntityType.NODE, localIdentity);
        em.persist(hidden);
        em.persist(kept);
        em.flush();

        for (User user : List.of(fixture.projectMember(ProjectMemberType.ADMIN), platformAdmin)) {
            String body = perform(user, get("/version/" + smuggled.getId() + "/conflicts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(kept.getId().toString()))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("Foreign secret"));
            assertFalse(body.contains(foreignParent.getId().toString()));
        }
    }

    @Test
    void listingAChildStillShowsConflictsOfItsOwnProject() throws Exception {
        UUID parentNode = fixture.addNode(NodeTypeEnum.LNG_CAMER);
        UUID child = createChild();
        UUID conflict = conflictOnRenamedParentNode(parentNode, child);

        perform(fixture.projectMember(ProjectMemberType.EDITOR), get("/version/" + child + "/conflicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(conflict.toString()))
                .andExpect(jsonPath("$.data[0].mergedDescription").value("LNG_CAMER \"Merged name\""));
    }

    private Version seededChild(Version parent, Project owner, BaseNode node, List<NodeChange> changes) {
        Version child = new Version("Seeded " + UUID.randomUUID(), parent, new ArrayList<>(List.of(node)),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(changes));
        em.persist(child);
        owner.addVersion(child);
        em.flush();
        return child;
    }

    private Well sameIdentityAs(BaseNode node) {
        Well copy = fixture.newNode(NodeTypeEnum.WELL);
        copy.setIdentityId(node.getIdentityId());
        return copy;
    }

    private long conflictsAgainst(UUID version) {
        em.flush();
        return em.createQuery("select count(c) from VersionConflict c where c.conflictingVersion.id = :version",
                        Long.class)
                .setParameter("version", version)
                .getSingleResult();
    }

    private UUID childThatDeletes(UUID identity) throws Exception {
        UUID child = createChild();
        UUID childNode = em.find(Version.class, child).getNodeSnapshot().stream()
                .filter(node -> identity.equals(node.getIdentityId()))
                .findFirst().orElseThrow().getId();
        perform(platformAdmin, delete("/version/" + child + "/node/" + childNode)).andExpect(status().isOk());
        em.flush();
        return child;
    }

    private UUID conflictOnRenamedParentNode(UUID parentNode, UUID child) {
        BaseNode node = em.find(BaseNode.class, parentNode);
        node.setName("Merged name");
        VersionConflict conflict = new VersionConflict(fixture.version(), fixture.version(), em.find(Version.class, child),
                ConflictEntityType.NODE, node.getIdentityId());
        em.persist(conflict);
        em.flush();
        return conflict.getId();
    }

    private UUID createChild() throws Exception {
        String body = perform(platformAdmin, post("/projects/" + fixture.project().getId() + "/version")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Child " + UUID.randomUUID() + "\",\"parentVersion\":\""
                        + fixture.version().getId() + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        em.flush();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private ResultActions merge(User user, UUID child) throws Exception {
        return perform(user, patch("/version/merge/" + child));
    }

    private ResultActions resolve(User user, UUID conflict) throws Exception {
        return perform(user, patch("/version/conflicts/" + conflict + "/resolve/ACCEPT"));
    }

    private UUID identityOf(UUID node) {
        return em.find(BaseNode.class, node).getIdentityId();
    }

    private boolean parentHolds(UUID identity) {
        em.flush();
        em.clear();
        return em.find(Version.class, fixture.version().getId()).getNodeSnapshot().stream()
                .anyMatch(node -> identity.equals(node.getIdentityId()));
    }

    private String childNodeName(UUID child, UUID identity) {
        em.flush();
        em.clear();
        return em.find(Version.class, child).getNodeSnapshot().stream()
                .filter(node -> identity.equals(node.getIdentityId()))
                .findFirst().orElseThrow().getName();
    }

    private void expectDifferentProjects(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(DIFFERENT_PROJECTS));
    }

    private void expectForbidden(ResultActions result, String message) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message));
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.bearer(user)));
    }
}
