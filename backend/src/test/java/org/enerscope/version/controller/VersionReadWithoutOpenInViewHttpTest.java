package org.enerscope.version.controller;

import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.version.model.enums.VersionMemberType;
import org.enerscope.version.service.VersionConflictService;
import org.enerscope.version.service.VersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false",
        "spring.datasource.url=jdbc:h2:mem:version-read-no-osiv;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class VersionReadWithoutOpenInViewHttpTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;
    @Autowired
    TransactionTemplate transaction;
    @Autowired
    VersionService versionService;
    @Autowired
    VersionConflictService conflictService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getVersionAuthorizesAVersionRoleHolderWithoutAnOpenSession() {
        Created created = create(VersionMemberType.ADMIN);
        authenticateAs(created.caller());

        assertEquals(created.versionId(), versionService.getVersion(created.versionId()).getId());
    }

    @Test
    void getVersionAuthorizesAProjectMemberWithoutAVersionRoleAndWithoutAnOpenSession() {
        Created created = create(null);
        authenticateAs(created.caller());

        assertEquals(created.versionId(), versionService.getVersion(created.versionId()).getId());
    }

    @Test
    void getConflictsForVersionAuthorizesAProjectMemberWithoutAVersionRoleAndWithoutAnOpenSession() {
        Created created = create(null);
        authenticateAs(created.caller());

        assertTrue(conflictService.getConflictsForVersion(created.versionId()).isEmpty());
    }

    @Test
    void theConflictsEndpointRespondsToAVersionRoleHolderWithoutOpenInView() throws Exception {
        Created created = create(VersionMemberType.ADMIN);

        mvc.perform(get("/version/" + created.versionId() + "/conflicts").header("Authorization", created.bearer()))
                .andExpect(status().isOk());
    }

    private Created create(VersionMemberType roleOrNull) {
        return transaction.execute(status -> {
            AccessHttpFixture fixture = new AccessHttpFixture(em, jwt);
            User caller = roleOrNull == null
                    ? fixture.projectMember(ProjectMemberType.EDITOR)
                    : fixture.projectEditorWithVersionRoles(roleOrNull);
            return new Created(fixture.version().getId(), caller, fixture.bearer(caller));
        });
    }

    private void authenticateAs(User user) {
        User claims = User.fromJwtClaims(user.getId(), user.getMail(), user.getFirstName(), user.getLastName(),
                user.getPlatformRole());
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(claims.getId(), null, List.of());
        token.setDetails(new Session("test", claims, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private record Created(UUID versionId, User caller, String bearer) {
    }
}
