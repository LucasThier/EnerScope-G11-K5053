package org.enerscope.organization.repository;

import org.enerscope.organization.dto.OrganizationDTO;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    @Query("""
            SELECT new org.enerscope.organization.dto.OrganizationDTO(
                    o.id, o.name, o.createdAt, o.active,
                    (SELECT COUNT(om) FROM OrganizationMember om WHERE om.organization = o))
            FROM Organization o
            ORDER BY o.name
            """)
    List<OrganizationDTO> findSummaries();

    @Query("""
            SELECT new org.enerscope.organization.dto.OrganizationDTO(
                    o.id, o.name, o.createdAt, o.active,
                    (SELECT COUNT(om) FROM OrganizationMember om WHERE om.organization = o))
            FROM Organization o
            JOIN o.members m
            WHERE o.active = true
              AND m.user.id = :userId
            ORDER BY o.name
            """)
    List<OrganizationDTO> findSummariesForMember(@Param("userId") UUID userId);

    @Query("""
            SELECT DISTINCT new org.enerscope.organization.dto.OrganizationDTO(
                    o.id, o.name, o.createdAt, o.active,
                    (SELECT COUNT(om) FROM OrganizationMember om WHERE om.organization = o))
            FROM Organization o
            JOIN o.members m
            JOIN m.roles r
            WHERE o.active = true
              AND m.user.id = :userId
              AND :permission MEMBER OF r.permissions
            ORDER BY o.name
            """)
    List<OrganizationDTO> findOwnedBy(@Param("userId") UUID userId,
                                      @Param("permission") OrganizationMemberPermission permission);

    @Query("""
            SELECT COUNT(DISTINCT o) > 0
            FROM Organization o
            JOIN o.members m
            JOIN m.roles r
            WHERE o.active = true
              AND m.user.id = :userId
              AND :permission MEMBER OF r.permissions
            """)
    boolean ownsAnyActiveOrganization(@Param("userId") UUID userId,
                                      @Param("permission") OrganizationMemberPermission permission);

    boolean existsByIdAndActiveTrue(UUID id);

    Optional<Organization> findByIdAndActiveTrue(UUID id);
}
