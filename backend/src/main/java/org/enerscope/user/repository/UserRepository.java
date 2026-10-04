package org.enerscope.user.repository;

import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByMailIgnoreCase(String mail);

    boolean existsByMailIgnoreCase(String mail);

    long countByPlatformRoleAndActiveTrue(PlatformRole platformRole);
}
