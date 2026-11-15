package org.enerscope.version.controller;

import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.enums.VersionMemberType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class VersionAccessHttpTest {

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
    void platformAdminGetsFullAccessWithoutBeingAMember() throws Exception {
        access(fixture.user(PlatformRole.ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platformAdmin").value(true))
                .andExpect(jsonPath("$.data.canManageProject").value(true))
                .andExpect(jsonPath("$.data.canCreateRootVersions").value(true))
                .andExpect(jsonPath("$.data.versions[0].source").value("PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.data.versions[0].canManage").value(true))
                .andExpect(jsonPath("$.data.versions[0].canManageRoles").value(true));
    }

    @Test
    void projectAdminManagesTheProjectAndEveryVersion() throws Exception {
        access(fixture.projectMember(ProjectMemberType.ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platformAdmin").value(false))
                .andExpect(jsonPath("$.data.projectRole").value("ADMIN"))
                .andExpect(jsonPath("$.data.canManageProject").value(true))
                .andExpect(jsonPath("$.data.canCreateRootVersions").value(true))
                .andExpect(jsonPath("$.data.versions[0].source").value("PROJECT_ADMIN"))
                .andExpect(jsonPath("$.data.versions[0].canManage").value(true))
                .andExpect(jsonPath("$.data.versions[0].canManageRoles").value(true))
                .andExpect(jsonPath("$.data.versions[0].effectiveRole").value("ADMIN"));
    }

    @Test
    void projectEditorEditsVersionsButCannotCreateRootsOrManage() throws Exception {
        access(fixture.projectMember(ProjectMemberType.EDITOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectRole").value("EDITOR"))
                .andExpect(jsonPath("$.data.canManageProject").value(false))
                .andExpect(jsonPath("$.data.canCreateRootVersions").value(false))
                .andExpect(jsonPath("$.data.versions[0].source").value("PROJECT_FALLBACK"))
                .andExpect(jsonPath("$.data.versions[0].canView").value(true))
                .andExpect(jsonPath("$.data.versions[0].canEdit").value(true))
                .andExpect(jsonPath("$.data.versions[0].canManage").value(false))
                .andExpect(jsonPath("$.data.versions[0].canManageRoles").value(false))
                .andExpect(jsonPath("$.data.versions[0].roles.length()").value(0));
    }

    @Test
    void projectExecutiveOnlyViews() throws Exception {
        access(fixture.projectMember(ProjectMemberType.EXECUTIVE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectRole").value("EXECUTIVE"))
                .andExpect(jsonPath("$.data.canManageProject").value(false))
                .andExpect(jsonPath("$.data.canCreateRootVersions").value(false))
                .andExpect(jsonPath("$.data.versions[0].source").value("PROJECT_FALLBACK"))
                .andExpect(jsonPath("$.data.versions[0].effectiveRole").value("EXECUTIVE"))
                .andExpect(jsonPath("$.data.versions[0].canView").value(true))
                .andExpect(jsonPath("$.data.versions[0].canEdit").value(false))
                .andExpect(jsonPath("$.data.versions[0].canEditConnections").value(false))
                .andExpect(jsonPath("$.data.versions[0].canSimulate").value(false))
                .andExpect(jsonPath("$.data.versions[0].canManage").value(false))
                .andExpect(jsonPath("$.data.versions[0].canManageRoles").value(false));
    }

    @Test
    void versionSimulatorCanOnlyViewAndSimulate() throws Exception {
        access(fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canCreateRootVersions").value(false))
                .andExpect(jsonPath("$.data.versions[0].source").value("VERSION_ROLES"))
                .andExpect(jsonPath("$.data.versions[0].roles[0].type").value("SIMULATOR"))
                .andExpect(jsonPath("$.data.versions[0].canView").value(true))
                .andExpect(jsonPath("$.data.versions[0].canSimulate").value(true))
                .andExpect(jsonPath("$.data.versions[0].canEdit").value(false))
                .andExpect(jsonPath("$.data.versions[0].canEditConnections").value(false))
                .andExpect(jsonPath("$.data.versions[0].canManage").value(false))
                .andExpect(jsonPath("$.data.versions[0].editableVerticals.length()").value(0));
    }

    @Test
    void versionEditorOfOneVerticalEditsThatVerticalAndInternalConsumptionOnly() throws Exception {
        access(fixture.versionEditorOf(VerticalEnum.EXTRACTION)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versions[0].source").value("VERSION_ROLES"))
                .andExpect(jsonPath("$.data.versions[0].roles[0].vertical").value("EXTRACTION"))
                .andExpect(jsonPath("$.data.versions[0].canSimulate").value(true))
                .andExpect(jsonPath("$.data.versions[0].canManage").value(false))
                .andExpect(jsonPath("$.data.versions[0].editableVerticals",
                        contains("EXTRACTION", "INTERNAL_CONSUMPTION")));
    }

    @Test
    void versionAdminWhoIsOnlyAProjectEditorManagesTheVersionButNotItsRoles() throws Exception {
        access(fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canManageProject").value(false))
                .andExpect(jsonPath("$.data.versions[0].source").value("VERSION_ROLES"))
                .andExpect(jsonPath("$.data.versions[0].canManage").value(true))
                .andExpect(jsonPath("$.data.versions[0].canManageRoles").value(false));
    }

    @Test
    void deactivatedVersionsAreLeftOutOfTheList() throws Exception {
        User editor = fixture.projectMember(ProjectMemberType.EDITOR);
        Version second = new Version("Second", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());
        em.persist(second);
        fixture.project().addVersion(second);
        em.flush();
        second.deactivate();
        em.flush();

        access(editor).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versions.length()").value(1))
                .andExpect(jsonPath("$.data.versions[0].versionId").value(fixture.version().getId().toString()));
    }

    @Test
    void aUserOutsideTheProjectGets403() throws Exception {
        access(fixture.organizationMember(OrganizationMemberType.MEMBER)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void anOrganizationOwnerOutsideTheProjectGets403() throws Exception {
        access(fixture.organizationMember(OrganizationMemberType.OWNER)).andExpect(status().isForbidden());
    }

    @Test
    void aDeactivatedProjectMemberGets403() throws Exception {
        User editor = fixture.projectMember(ProjectMemberType.EDITOR);
        em.createQuery("select m from ProjectMember m where m.user.id = :user", ProjectMember.class)
                .setParameter("user", editor.getId()).getSingleResult().deactivate();
        em.flush();

        access(editor).andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousCallerGets401() throws Exception {
        mvc.perform(get(path(fixture.project().getId()))).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownProjectAnswers404ForAPlatformAdmin() throws Exception {
        mvc.perform(get(path(UUID.randomUUID())).header("Authorization", fixture.bearer(fixture.user(PlatformRole.ADMIN))))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnknownProjectAnswers403ForANonAdminWithTheSameMessageAsARealOutsider() throws Exception {
        User outsider = fixture.organizationMember(OrganizationMemberType.MEMBER);
        access(outsider).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not allowed to view this project"));
        mvc.perform(get(path(UUID.randomUUID())).header("Authorization", fixture.bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not allowed to view this project"));
    }

    @Test
    void anUnknownProjectAnswers403ForAProjectMemberOfAnotherProject() throws Exception {
        mvc.perform(get(path(UUID.randomUUID())).header("Authorization",
                        fixture.bearer(fixture.projectMember(ProjectMemberType.ADMIN))))
                .andExpect(status().isForbidden());
    }

    private ResultActions access(User user) throws Exception {
        return mvc.perform(get(path(fixture.project().getId())).header("Authorization", fixture.bearer(user)));
    }

    private String path(UUID projectId) {
        return "/projects/" + projectId + "/access";
    }
}
