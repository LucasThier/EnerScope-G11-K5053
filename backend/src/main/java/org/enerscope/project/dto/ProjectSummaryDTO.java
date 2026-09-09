package org.enerscope.project.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A project as it appears in a list: the owning organization is resolved to its
 * name and the members are reduced to a count, so a listing renders without
 * further calls and without loading either association.
 *
 * <p>Built directly by a JPQL projection ({@code ProjectRepository}); the
 * richer {@link ProjectDTO} stays the shape returned when a single project is
 * created.</p>
 */
public record ProjectSummaryDTO(
        UUID id,
        String name,
        String description,
        UUID organizationId,
        String organizationName,
        long memberCount,
        Instant lastModified
) {}
