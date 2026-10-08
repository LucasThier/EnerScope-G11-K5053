package org.enerscope.organization.dto;

import jakarta.validation.constraints.NotNull;
import org.enerscope.organization.model.enums.OrganizationMemberType;

public record UpdateOrganizationMemberRoleRequestDTO(
        @NotNull
        OrganizationMemberType memberType
) {}
