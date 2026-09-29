package org.enerscope.organization.dto;

import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.model.enums.OrganizationMemberType;

import java.util.Set;
import java.util.UUID;

/**
 * A member of an organization, flattened with the identity fields the team
 * screens list (name, mail, job title) so the client never needs a second call
 * per row.
 *
 * <p>{@code active} mirrors the user account state: there is no separate
 * membership status yet, so an invited-but-not-yet-active member cannot be
 * represented (see {@code docs/considerations.md}).</p>
 */
public record OrganizationMemberDTO(
        UUID id,
        UUID userId,
        String userMail,
        String firstName,
        String lastName,
        String jobTitle,
        boolean active,
        OrganizationMemberType memberType,
        Set<OrganizationMemberPermission> permissions
) {}
