package org.enerscope.version.controller;

import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.ConflictEntityType;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionConflict;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class VersionConflictsListAccessHttpTest {

    private static final String ADMIN_ONLY_MESSAGE = "Only platform admins can list every conflict";

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;

    private AccessHttpFixture fixture;
    private VersionConflict projectConflict;
    private VersionConflict detachedConflict;

    @BeforeEach
    void setUp() {
        fixture = new AccessHttpFixture(em, jwt);
        Version version = fixture.version();
        Version detached = fixture.detachedVersion();
        projectConflict = new VersionConflict(version, version, version, ConflictEntityType.NODE, UUID.randomUUID());
        detachedConflict = new VersionConflict(detached, detached, detached, ConflictEntityType.NODE, UUID.randomUUID());
        em.persist(projectConflict);
        em.persist(detachedConflict);
        em.flush();
    }

    @Test
    void platformAdminListsEveryConflict() throws Exception {
        mvc.perform(get("/version/conflicts").header("Authorization", fixture.bearer(fixture.user(PlatformRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].id",
                        hasItems(projectConflict.getId().toString(), detachedConflict.getId().toString())));
    }

    @Test
    void projectAdminGets403() throws Exception {
        assertRefused(fixture.projectMember(ProjectMemberType.ADMIN));
    }

    @Test
    void projectEditorGets403() throws Exception {
        assertRefused(fixture.projectMember(ProjectMemberType.EDITOR));
    }

    @Test
    void versionEditorOfAnotherVerticalGets403() throws Exception {
        User editor = fixture.projectMember(ProjectMemberType.EDITOR);
        VersionMember member = new VersionMember(editor, fixture.version());
        member.addRole(new VersionMemberRole("EDITOR", VersionMemberType.EDITOR,
                EnumSet.copyOf(VersionMember.defaultPermissionsFor(VersionMemberType.EDITOR)), VerticalEnum.EXPORT));
        em.persist(member);
        em.flush();

        assertRefused(editor);
    }

    @Test
    void versionSimulatorGets403() throws Exception {
        assertRefused(fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR));
    }

    @Test
    void unrelatedUserGets403() throws Exception {
        assertRefused(fixture.organizationMember(OrganizationMemberType.MEMBER));
    }

    @Test
    void organizationOwnerOutsideTheProjectGets403() throws Exception {
        assertRefused(fixture.organizationMember(OrganizationMemberType.OWNER));
    }

    @Test
    void anonymousCallerGets401() throws Exception {
        mvc.perform(get("/version/conflicts")).andExpect(status().isUnauthorized());
    }

    private void assertRefused(User user) throws Exception {
        mvc.perform(get("/version/conflicts").header("Authorization", fixture.bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ADMIN_ONLY_MESSAGE))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
