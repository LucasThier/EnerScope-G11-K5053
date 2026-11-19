package org.enerscope.version.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.VersionMember;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class VersionRoleCopyDeactivatedUsersHttpTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;
    @Autowired
    VersionMemberRepository versionMembers;

    private AccessHttpFixture fixture;
    private User platformAdmin;

    @BeforeEach
    void setUp() {
        fixture = new AccessHttpFixture(em, jwt);
        platformAdmin = fixture.user(PlatformRole.ADMIN);
    }

    @Test
    void aBranchCopiesTheRolesOfActiveMembersAndTheyKeepWorkingThere() throws Exception {
        User admin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);

        UUID child = branch();

        assertEquals(List.of(admin.getId()), usersWithRolesIn(child));
        mvc.perform(get("/version/" + child + "/diagram").header("Authorization", fixture.bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void aBranchCopiesTheRolesOfADeactivatedProjectMemberButTheyGrantNoAccessThere() throws Exception {
        User admin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        User removed = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        projectMemberOf(removed).deactivate();
        em.flush();

        UUID child = branch();

        assertTrue(usersWithRolesIn(child).contains(removed.getId()));
        assertTrue(usersWithRolesIn(child).contains(admin.getId()));
        assertTrue(versionMembers.findEffectiveByVersionIdAndUserId(child, removed.getId()).isEmpty());
        assertTrue(versionMembers.findEffectiveByVersionIdAndUserId(child, admin.getId()).isPresent());
        mvc.perform(get("/version/" + child + "/diagram").header("Authorization", fixture.bearer(removed)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aBranchCopiesTheRolesOfADeactivatedUserAccountButTheyAreNeverEffectiveOnceTheMembershipIsDeactivated()
            throws Exception {
        User deactivated = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        deactivated.deactivate();
        projectMemberOf(deactivated).deactivate();
        em.flush();

        UUID child = branch();

        assertTrue(usersWithRolesIn(child).contains(deactivated.getId()));
        assertTrue(versionMembers.findEffectiveByVersionIdAndUserId(child, deactivated.getId()).isEmpty());
    }

    private UUID branch() throws Exception {
        String body = mvc.perform(post("/projects/" + fixture.project().getId() + "/version")
                        .header("Authorization", fixture.bearer(platformAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Branch " + UUID.randomUUID() + "\",\"parentVersion\":\""
                                + fixture.version().getId() + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        em.flush();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private List<UUID> usersWithRolesIn(UUID version) {
        em.flush();
        return em.createQuery("select m from VersionMember m where m.version.id = :version", VersionMember.class)
                .setParameter("version", version).getResultList().stream()
                .filter(member -> !member.getRoles().isEmpty())
                .map(member -> member.getUser().getId())
                .toList();
    }

    private ProjectMember projectMemberOf(User user) {
        return em.createQuery("select m from ProjectMember m where m.user.id = :user and m.project.id = :project",
                        ProjectMember.class)
                .setParameter("user", user.getId())
                .setParameter("project", fixture.project().getId())
                .getSingleResult();
    }
}
