package org.enerscope.user.dto;

import org.enerscope.organization.model.enums.OrganizationMemberType;

import java.util.UUID;

/**
 * One organization a user belongs to, as seen from {@code GET /users/{id}}.
 * Carries {@code organizationActive} rather than filtering suspended
 * organizations out: the admin detail view marks them instead of hiding them.
 */
public record UserOrganizationMembershipDTO(
        UUID organizationId,
        String organizationName,
        boolean organizationActive,
        OrganizationMemberType memberType
) {}
