package org.enerscope.version.dto;

import java.time.Instant;
import java.util.UUID;

public record VersionDTO(
        UUID id,
        String name,
        Instant createdAt,
        UUID projectId,
        UUID parentVersionId
) {}
