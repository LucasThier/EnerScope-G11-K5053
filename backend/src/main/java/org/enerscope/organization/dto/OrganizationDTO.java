package org.enerscope.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record OrganizationDTO(
        UUID id,
        String name,
        Instant createdAt
) {}
