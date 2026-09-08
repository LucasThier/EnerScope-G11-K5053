package org.enerscope.organization.dto;

import jakarta.validation.constraints.NotNull;
import org.enerscope.organization.model.enums.OrganizationMemberType;

import java.util.UUID;

public record AddOrganizationMemberRequestDTO(
        @NotNull
        UUID userId,

        @NotNull
        OrganizationMemberType memberType
) {}
