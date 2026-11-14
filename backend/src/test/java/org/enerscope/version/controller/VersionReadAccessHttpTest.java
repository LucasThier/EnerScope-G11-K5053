package org.enerscope.version.controller;

import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.enums.VersionMemberType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class VersionReadAccessHttpTest {

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

    @Test
    void platformAdminReadsDiagramAndNodeWithoutMembership() throws Exception {
        assertReadsDiagramAndNode(fixture.user(PlatformRole.ADMIN));
    }

    @Test
    void projectAdminReadsDiagramAndNode() throws Exception {
        assertReadsDiagramAndNode(fixture.projectMember(ProjectMemberType.ADMIN));
    }

    @Test
    void projectEditorReadsDiagramAndNode() throws Exception {
        assertReadsDiagramAndNode(fixture.projectMember(ProjectMemberType.EDITOR));
    }

    @Test
    void versionSimulatorReadsDiagramAndNode() throws Exception {
        assertReadsDiagramAndNode(fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR));
    }

    @Test
    void versionEditorOfAnotherVerticalReadsDiagramAndNode() throws Exception {
        User editor = fixture.projectMember(ProjectMemberType.EDITOR);
        org.enerscope.version.model.VersionMember member = new org.enerscope.version.model.VersionMember(editor, fixture.version());
        member.addRole(new org.enerscope.version.model.VersionMemberRole("EDITOR", VersionMemberType.EDITOR,
                java.util.EnumSet.copyOf(org.enerscope.version.model.VersionMember.defaultPermissionsFor(VersionMemberType.EDITOR)),
                org.enerscope.node.model.enums.VerticalEnum.EXPORT));
        em.persist(member);
        em.flush();

        assertReadsDiagramAndNode(editor);
    }

    @Test
    void unrelatedUserGets403WithProjectMessage() throws Exception {
        assertDiagramAndNodeAre(fixture.organizationMember(OrganizationMemberType.MEMBER),
                status().isForbidden(), "You are not allowed to view this project");
    }

    @Test
    void organizationOwnerOutsideTheProjectGets403() throws Exception {
        assertDiagramAndNodeAre(fixture.organizationMember(OrganizationMemberType.OWNER),
                status().isForbidden(), "You are not allowed to view this project");
    }

    @Test
    void versionWithoutProjectGets403WithVersionMessage() throws Exception {
        User user = fixture.projectMember(ProjectMemberType.ADMIN);
        UUID detached = fixture.detachedVersion().getId();

        mvc.perform(get("/version/" + detached + "/diagram").header("Authorization", fixture.bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You are not allowed to access this version"));
    }

    @Test
    void missingVersionGets404BeforeTheAccessCheck() throws Exception {
        User outsider = fixture.user(PlatformRole.USER);

        mvc.perform(get("/version/" + UUID.randomUUID() + "/diagram").header("Authorization", fixture.bearer(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", startsWith("Version not found with id:")));
    }

    @Test
    void anonymousCallerGets401() throws Exception {
        mvc.perform(get(diagramPath())).andExpect(status().isUnauthorized());
        mvc.perform(get(nodePath())).andExpect(status().isUnauthorized());
    }

    private void assertReadsDiagramAndNode(User user) throws Exception {
        mvc.perform(get(diagramPath()).header("Authorization", fixture.bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value(fixture.version().getId().toString()))
                .andExpect(jsonPath("$.data.nodes.length()").value(fixture.version().getNodeSnapshot().size()));
        mvc.perform(get(nodePath()).header("Authorization", fixture.bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(fixture.nodeId().toString()));
    }

    private void assertDiagramAndNodeAre(User user, ResultMatcher expectedStatus, String message) throws Exception {
        mvc.perform(get(diagramPath()).header("Authorization", fixture.bearer(user)))
                .andExpect(expectedStatus)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message));
        mvc.perform(get(nodePath()).header("Authorization", fixture.bearer(user)))
                .andExpect(expectedStatus)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message));
    }

    private String diagramPath() {
        return "/version/" + fixture.version().getId() + "/diagram";
    }

    private String nodePath() {
        return "/version/" + fixture.version().getId() + "/node/" + fixture.nodeId();
    }
}
