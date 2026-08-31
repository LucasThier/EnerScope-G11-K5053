package org.enerscope.organization.repository;

import org.enerscope.organization.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    /** Organizations the given user is a member of. */
    List<Organization> findDistinctByMembers_User_Id(UUID userId);
}
