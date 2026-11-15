package org.enerscope.version.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /projects/{id}/version-members}: the role matrix of a project's active versions. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ProjectVersionMembersHttpTest {

    private static final String DENIED = "You are not allowed to manage this project";

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;
    @Autowired
    ObjectMapper json;

    private AccessHttpFixture fixture;
    private UUID projectId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        fixture = new AccessHttpFixture(em, jwt);
        projectId = fixture.project().getId();
        versionId = fixture.version().getId();
    }

    @Test
    void projectAdminAndPlatformAdminReadTheMatrix() throws Exception {
        User editor = fixture.projectEditorWithVersionRoles(VersionMemberType.EDITOR, VersionMemberType.SIMULATOR);

        for (User caller : List.of(fixture.projectMember(ProjectMemberType.ADMIN), fixture.user(PlatformRole.ADMIN))) {
            matrix(caller).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].versionId").value(versionId.toString()))
                    .andExpect(jsonPath("$.data[0].versionName").value("Access fixture"))
                    .andExpect(jsonPath("$.data[0].members.length()").value(1))
                    .andExpect(jsonPath("$.data[0].members[0].userId").value(editor.getId().toString()))
                    .andExpect(jsonPath("$.data[0].members[0].roles.length()").value(2));
        }
    }

    @Test
    void everyoneElseGets403() throws Exception {
        User[] refused = {
                fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN),
                fixture.projectEditorWithVersionRoles(VersionMemberType.EDITOR),
                fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR),
                fixture.projectMember(ProjectMemberType.EDITOR),
                fixture.user(PlatformRole.USER)};

        for (User caller : refused) {
            matrix(caller).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(DENIED));
        }
    }

    @Test
    void unknownProjectIs403ForAProjectAdminAnd404ForAPlatformAdmin() throws Exception {
        UUID unknown = UUID.randomUUID();

        mvc.perform(get("/projects/" + unknown + "/version-members")
                        .header("Authorization", fixture.bearer(fixture.projectMember(ProjectMemberType.ADMIN))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/projects/" + unknown + "/version-members")
                        .header("Authorization", fixture.bearer(fixture.user(PlatformRole.ADMIN))))
                .andExpect(status().isNotFound());
    }

    @Test
    void inactiveVersionsAreLeftOutAndAVersionWithoutMembersHasAnEmptyList() throws Exception {
        fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        Version empty = addVersion("Empty");
        Version inactive = addVersion("Inactive");
        inactive.deactivate();
        em.flush();

        JsonNode data = read(matrix(fixture.projectMember(ProjectMemberType.ADMIN)));

        List<String> ids = StreamSupport.stream(data.spliterator(), false)
                .map(entry -> entry.get("versionId").asText()).toList();
        assertTrue(ids.contains(versionId.toString()));
        assertTrue(ids.contains(empty.getId().toString()));
        assertTrue(!ids.contains(inactive.getId().toString()));
        for (JsonNode entry : data) {
            if (entry.get("versionId").asText().equals(empty.getId().toString())) {
                assertEquals(0, entry.get("members").size());
            }
        }
    }

    @Test
    void eachVersionOfTheMatrixMatchesTheListingOfThatVersion() throws Exception {
        fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        fixture.projectEditorWithVersionRoles(VersionMemberType.EDITOR, VersionMemberType.SIMULATOR);
        Version second = addVersion("Second");
        User onSecond = fixture.projectMember(ProjectMemberType.EDITOR);
        fixture.grantVersionRole(onSecond, second, VersionMemberType.SIMULATOR, null,
                org.enerscope.version.model.VersionMember.defaultPermissionsFor(VersionMemberType.SIMULATOR));
        User projectAdmin = fixture.projectMember(ProjectMemberType.ADMIN);

        JsonNode data = read(matrix(projectAdmin));

        assertEquals(2, data.size());
        for (JsonNode entry : data) {
            JsonNode listing = read(mvc.perform(get("/version/" + entry.get("versionId").asText() + "/members")
                    .header("Authorization", fixture.bearer(projectAdmin))));
            assertEquals(byMemberId(listing), byMemberId(entry.get("members")));
        }
    }

    private Version addVersion(String name) {
        Version version = new Version(name, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        em.persist(version);
        fixture.project().addVersion(version);
        em.flush();
        return version;
    }

    private ResultActions matrix(User caller) throws Exception {
        return mvc.perform(get("/projects/" + projectId + "/version-members")
                .header("Authorization", fixture.bearer(caller)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private List<String> byMemberId(JsonNode members) {
        return StreamSupport.stream(members.spliterator(), false)
                .sorted(Comparator.comparing(member -> member.get("id").asText()))
                .map(JsonNode::toString)
                .toList();
    }
}
