package org.enerscope.version.controller;

import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false",
        "spring.datasource.url=jdbc:h2:mem:version-get-json-no-osiv;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class VersionGetJsonWithoutOpenInViewHttpTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;
    @Autowired
    TransactionTemplate transaction;

    @Test
    void getVersionJsonMatchesTheGoldenFileWithoutOpenInView() throws Exception {
        VersionJsonGolden.Created created = VersionJsonGolden.createChildWithParentNodeConnectionAndChange(em, jwt, transaction);

        String body = mvc.perform(get("/version/" + created.versionId()).header("Authorization", created.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        VersionJsonGolden.assertMatchesGolden(body);
    }

    @Test
    void getVersionStillAnswers403ForAnOutsiderWithoutOpenInView() throws Exception {
        VersionJsonGolden.Created created = VersionJsonGolden.createChildWithParentNodeConnectionAndChange(em, jwt, transaction);
        String outsider = transaction.execute(status -> {
            AccessHttpFixture other = new AccessHttpFixture(em, jwt);
            return other.bearer(other.projectMember(org.enerscope.project.model.enums.ProjectMemberType.EDITOR));
        });

        mvc.perform(get("/version/" + created.versionId()).header("Authorization", outsider))
                .andExpect(status().isForbidden());
    }

    @Test
    void getVersionAnswers404ForAPlatformAdminAndAnUnknownId() throws Exception {
        String admin = transaction.execute(status -> {
            AccessHttpFixture other = new AccessHttpFixture(em, jwt);
            return other.bearer(other.user(org.enerscope.user.model.enums.PlatformRole.ADMIN));
        });

        mvc.perform(get("/version/" + java.util.UUID.randomUUID()).header("Authorization", admin))
                .andExpect(status().isNotFound());
    }
}
