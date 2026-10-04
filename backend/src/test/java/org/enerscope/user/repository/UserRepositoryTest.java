package org.enerscope.user.repository;

import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persist(String mail, PlatformRole role, boolean active) {
        User user = new User(mail, "First", "Last", "hashed", role);
        if (!active) {
            user.deactivate();
        }
        return entityManager.persist(user);
    }

    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void countsTheActiveAdmins() {
        persist("count-admin-1@enerscope.org", PlatformRole.ADMIN, true);
        persist("count-admin-2@enerscope.org", PlatformRole.ADMIN, true);
        sync();

        assertEquals(2L, userRepository.countByPlatformRoleAndActiveTrue(PlatformRole.ADMIN));
    }

    @Test
    void excludesInactiveAdmins() {
        persist("count-active@enerscope.org", PlatformRole.ADMIN, true);
        persist("count-suspended@enerscope.org", PlatformRole.ADMIN, false);
        sync();

        assertEquals(1L, userRepository.countByPlatformRoleAndActiveTrue(PlatformRole.ADMIN));
    }

    @Test
    void excludesRegularUsers() {
        persist("count-only-admin@enerscope.org", PlatformRole.ADMIN, true);
        persist("count-user-1@enerscope.org", PlatformRole.USER, true);
        persist("count-user-2@enerscope.org", PlatformRole.USER, true);
        sync();

        assertEquals(1L, userRepository.countByPlatformRoleAndActiveTrue(PlatformRole.ADMIN));
    }

    @Test
    void countsZeroWhenEveryAdminIsInactive() {
        persist("count-all-suspended@enerscope.org", PlatformRole.ADMIN, false);
        persist("count-plain@enerscope.org", PlatformRole.USER, true);
        sync();

        assertEquals(0L, userRepository.countByPlatformRoleAndActiveTrue(PlatformRole.ADMIN));
    }
}
