package org.enerscope.organization.controller;

import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:bulk-upload-limit;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class BulkUploadSizeLimitHttpTest {

    private static final int ONE_MB = 1024 * 1024;

    @Autowired
    TestRestTemplate rest;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;
    @Autowired
    TransactionTemplate transaction;

    private String bearer;

    @BeforeEach
    void setUp() {
        User admin = transaction.execute(status -> {
            User user = new User("upload-" + UUID.randomUUID() + "@example.com", "Upload", "Admin", "hashed",
                    PlatformRole.ADMIN);
            em.persist(user);
            return user;
        });
        bearer = "Bearer " + jwt.generateAccessToken(User.fromJwtClaims(admin.getId(), admin.getMail(),
                admin.getFirstName(), admin.getLastName(), PlatformRole.ADMIN));
    }

    @Test
    void aCsvAboveOneMegabyteAnswers413InsteadOf500() {
        var response = upload(ONE_MB + 1);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertTrue(response.getBody().contains("The uploaded file exceeds the maximum size of 1 MB"));
    }

    @Test
    void aCsvOfExactlyOneMegabyteIsNotRejectedForItsSize() {
        var response = upload(ONE_MB);

        assertNotEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
    }

    private org.springframework.http.ResponseEntity<String> upload(int bytes) {
        byte[] content = new byte[bytes];
        java.util.Arrays.fill(content, (byte) 'a');
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "users.csv";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add("Authorization", bearer);
        return rest.postForEntity("/api/v1/organizations/" + UUID.randomUUID() + "/users/bulk",
                new HttpEntity<>(body, headers), String.class);
    }
}
