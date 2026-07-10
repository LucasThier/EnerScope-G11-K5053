package org.enerscope;

import org.enerscope.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test: the full Spring context wires up (security, filters, JWT, JPA,
 * OpenAPI, seeder) and the admin seeder runs on startup.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void contextLoadsAndSeedsAdmin() {
        assertTrue(userRepository.existsByMailIgnoreCase("admin@enerscope.org"));
    }
}
