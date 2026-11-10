package org.enerscope.organization.dto;

import org.enerscope.organization.model.enums.OrganizationMemberType;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code memberType} is the caller's role in the organization, or {@code null}
 * when the caller has no active membership in it (e.g. a platform admin
 * looking at an organization they do not belong to).
 */
public record OrganizationDTO(
        UUID id,
        String name,
        Instant createdAt,
        boolean active,
        long memberCount,
        OrganizationMemberType memberType
) {
    public OrganizationDTO(UUID id, String name, Instant createdAt, boolean active, long memberCount) {
        this(id, name, createdAt, active, memberCount, null);
    }

    public OrganizationDTO withMemberType(OrganizationMemberType memberType) {
        return new OrganizationDTO(id, name, createdAt, active, memberCount, memberType);
    }
}
