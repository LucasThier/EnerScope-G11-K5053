package org.enerscope.version.controller;

import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class VersionInactiveAccessHttpTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;

    private AccessHttpFixture fixture;
    private User versionAdmin;

    @BeforeEach
    void setUp() {
        fixture = new AccessHttpFixture(em, jwt);
        versionAdmin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
    }

    @Test
    void aVersionRoleHolderReadsWhileTheProjectAndTheVersionAreActive() throws Exception {
        assertReads(versionAdmin, 200);
    }

    @Test
    void aVersionRoleHolderIsRefusedOnceTheVersionIsDeactivated() throws Exception {
        deactivateVersion();

        assertReads(versionAdmin, 403);
    }

    @Test
    void aVersionRoleHolderIsRefusedOnceTheProjectIsDeactivated() throws Exception {
        deactivateProject();

        assertReads(versionAdmin, 403);
    }

    @Test
    void aVersionSimulatorIsRefusedOnceTheVersionIsDeactivated() throws Exception {
        User simulator = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        deactivateVersion();

        assertReads(simulator, 403);
    }

    @Test
    void aPlatformAdminStillReadsADeactivatedVersion() throws Exception {
        User platformAdmin = fixture.user(PlatformRole.ADMIN);
        deactivateVersion();

        mvc.perform(get(versionPath()).header("Authorization", fixture.bearer(platformAdmin)))
                .andExpect(status().isOk());
        mvc.perform(get(versionPath() + "/diagram").header("Authorization", fixture.bearer(platformAdmin)))
                .andExpect(status().isOk());
    }

    private void assertReads(User user, int expected) throws Exception {
        String bearer = fixture.bearer(user);
        mvc.perform(get(versionPath()).header("Authorization", bearer)).andExpect(status().is(expected));
        mvc.perform(get(versionPath() + "/diagram").header("Authorization", bearer)).andExpect(status().is(expected));
        mvc.perform(get(versionPath() + "/conflicts").header("Authorization", bearer)).andExpect(status().is(expected));
    }

    private void deactivateVersion() {
        fixture.version().deactivate();
        em.flush();
    }

    private void deactivateProject() {
        fixture.project().deactivate();
        em.flush();
    }

    private String versionPath() {
        return "/version/" + fixture.version().getId();
    }
}
