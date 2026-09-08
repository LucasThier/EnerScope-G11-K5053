package org.enerscope.organization.dto;

import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.model.enums.OrganizationMemberType;

import java.util.Set;
import java.util.UUID;

public record OrganizationMemberDTO(
        UUID id,
        UUID userId,
        String userMail,
        OrganizationMemberType memberType,
        Set<OrganizationMemberPermission> permissions
) {}
