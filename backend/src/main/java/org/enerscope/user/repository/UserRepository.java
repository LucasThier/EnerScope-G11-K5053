package org.enerscope.user.repository;

import org.enerscope.user.dto.UserListItemDTO;
import org.enerscope.user.dto.UserOrganizationMembershipDTO;
import org.enerscope.user.dto.UserProjectMembershipDTO;
import org.enerscope.user.dto.UserSearchResultDTO;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByMailIgnoreCase(String mail);

    boolean existsByMailIgnoreCase(String mail);

    long countByPlatformRoleAndActiveTrue(PlatformRole platformRole);

    @Query("""
            SELECT new org.enerscope.user.dto.UserListItemDTO(
                    u.id, u.mail, u.firstName, u.lastName, u.jobTitle, u.platformRole, u.active,
                    (SELECT COUNT(om) FROM OrganizationMember om WHERE om.user = u))
            FROM User u
            ORDER BY u.firstName, u.lastName
            """)
    List<UserListItemDTO> findListItems();

    @Query("""
            SELECT new org.enerscope.user.dto.UserSearchResultDTO(
                    u.id, u.firstName, u.lastName, u.mail)
            FROM User u
            WHERE LOWER(u.mail) = LOWER(:mail)
              AND u.active = true
            """)
    Optional<UserSearchResultDTO> findSearchResultByMail(@Param("mail") String mail);

    /**
     * Every organization the user belongs to, active or suspended: the detail
     * view marks a suspended one rather than hiding it, so there is no
     * {@code o.active} filter here (unlike {@code findProjectMembershipsForUser},
     * where a deactivated project itself is still left out).
     */
    @Query("""
            SELECT new org.enerscope.user.dto.UserOrganizationMembershipDTO(
                    o.id, o.name, o.active, r.memberType)
            FROM OrganizationMember m
            JOIN m.organization o
            JOIN m.roles r
            WHERE m.user.id = :userId
            ORDER BY o.name
            """)
    List<UserOrganizationMembershipDTO> findOrganizationMembershipsForUser(@Param("userId") UUID userId);

    /**
     * Every active project the user belongs to, same {@code p.active} filter
     * {@code GET /projects} already applies. The owning organization's
     * {@code active} flag is carried, not filtered on, so a project under a
     * suspended organization still shows, marked.
     */
    @Query("""
            SELECT new org.enerscope.user.dto.UserProjectMembershipDTO(
                    p.id, p.name, o.id, o.name, o.active, r.memberType)
            FROM ProjectMember m
            JOIN m.project p
            JOIN p.organization o
            JOIN m.roles r
            WHERE m.user.id = :userId
              AND p.active = true
            ORDER BY p.name
            """)
    List<UserProjectMembershipDTO> findProjectMembershipsForUser(@Param("userId") UUID userId);
}
