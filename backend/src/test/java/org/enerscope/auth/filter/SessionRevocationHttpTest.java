package org.enerscope.auth.filter;

import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.controller.AccessHttpFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
class SessionRevocationHttpTest {

    private static final String PASSWORD = "Sup3r-secret!";

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;
    @Autowired
    PasswordEncoder encoder;

    private AccessHttpFixture fixture;
    private User platformAdmin;

    @BeforeEach
    void setUp() {
        fixture = new AccessHttpFixture(em, jwt);
        platformAdmin = fixture.user(PlatformRole.ADMIN);
    }

    @Test
    void deactivatedUserIsRejectedImmediatelyWithTokenIssuedBefore() throws Exception {
        User user = fixture.user(PlatformRole.USER);
        String earlierToken = fixture.bearer(user);
        mvc.perform(get("/organizations").header("Authorization", earlierToken))
                .andExpect(status().isOk());

        mvc.perform(delete("/users/" + user.getId()).header("Authorization", fixture.bearer(platformAdmin)))
                .andExpect(status().isOk());

        mvc.perform(get("/organizations").header("Authorization", earlierToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void deactivatedAdminLosesAdminEndpointsWithTokenIssuedBefore() throws Exception {
        User otherAdmin = fixture.user(PlatformRole.ADMIN);
        String earlierToken = fixture.bearer(otherAdmin);

        mvc.perform(delete("/users/" + otherAdmin.getId()).header("Authorization", fixture.bearer(platformAdmin)))
                .andExpect(status().isOk());

        mvc.perform(get("/users").header("Authorization", earlierToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfUserMissingFromTheDatabaseIsRejected() throws Exception {
        String ghostToken = "Bearer " + jwt.generateAccessToken(User.fromJwtClaims(UUID.randomUUID(),
                "ghost@example.com", "Ghost", "Admin", PlatformRole.ADMIN));

        mvc.perform(get("/users").header("Authorization", ghostToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void demotedAdminLosesAdminAccessImmediatelyWithTokenIssuedBefore() throws Exception {
        User demoted = fixture.user(PlatformRole.ADMIN);
        String earlierToken = fixture.bearer(demoted);
        mvc.perform(get("/users").header("Authorization", earlierToken))
                .andExpect(status().isOk());

        mvc.perform(patch("/users/" + demoted.getId() + "/role")
                        .header("Authorization", fixture.bearer(platformAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platformRole\":\"USER\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/users").header("Authorization", earlierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only platform admins can list platform users"));
        mvc.perform(post("/auth/register").header("Authorization", earlierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mail\":\"new-" + UUID.randomUUID() + "@example.com\",\"password\":\"Passw0rd!x\","
                                + "\"firstName\":\"New\",\"lastName\":\"User\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/organizations").header("Authorization", earlierToken))
                .andExpect(status().isOk());
    }

    @Test
    void promotedUserGainsAdminAccessWithTokenIssuedBefore() throws Exception {
        User promoted = fixture.user(PlatformRole.USER);
        String earlierToken = fixture.bearer(promoted);
        mvc.perform(get("/users").header("Authorization", earlierToken))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/users/" + promoted.getId() + "/role")
                        .header("Authorization", fixture.bearer(platformAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platformRole\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/users").header("Authorization", earlierToken))
                .andExpect(status().isOk());
    }

    @Test
    void activeUnchangedSessionsKeepWorking() throws Exception {
        User user = fixture.user(PlatformRole.USER);
        String userToken = fixture.bearer(user);
        String adminToken = fixture.bearer(platformAdmin);

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/organizations").header("Authorization", userToken))
                    .andExpect(status().isOk());
            mvc.perform(get("/users").header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void reactivatedUserCanUseTokenIssuedBeforeDeactivationAgain() throws Exception {
        User user = fixture.user(PlatformRole.USER);
        String earlierToken = fixture.bearer(user);
        String adminToken = fixture.bearer(platformAdmin);

        mvc.perform(delete("/users/" + user.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/organizations").header("Authorization", earlierToken))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/users/" + user.getId() + "/reactivate").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/organizations").header("Authorization", earlierToken))
                .andExpect(status().isOk());
    }

    @Test
    void publicAndLoginRoutesIgnoreARevokedBearer() throws Exception {
        User deactivated = fixture.user(PlatformRole.USER);
        String revokedToken = fixture.bearer(deactivated);
        User loginUser = new User("login-" + UUID.randomUUID() + "@example.com", "Login", "User",
                encoder.encode(PASSWORD), PlatformRole.USER);
        em.persist(loginUser);
        mvc.perform(delete("/users/" + deactivated.getId()).header("Authorization", fixture.bearer(platformAdmin)))
                .andExpect(status().isOk());

        mvc.perform(get("/health").header("Authorization", revokedToken))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/logout").header("Authorization", revokedToken))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/login").header("Authorization", revokedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mail\":\"" + loginUser.getMail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.mail").value(loginUser.getMail()));
    }

    @Test
    void refreshOfDeactivatedAccountStaysRejected() throws Exception {
        User user = fixture.user(PlatformRole.USER);
        String refreshToken = jwt.generateRefreshToken(user);
        mvc.perform(delete("/users/" + user.getId()).header("Authorization", fixture.bearer(platformAdmin)))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("This account has been deactivated"));
    }
}
