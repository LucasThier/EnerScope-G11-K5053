package org.enerscope.simulation.dto;

import java.util.List;
import java.util.UUID;

/**
 * One node that differs between a version and the comparison's baseline,
 * matched by its cross-version {@code identityId}.
 *
 * @param change        {@code ADDED} (only in this version), {@code REMOVED} (only in the baseline, or
 *                      soft-deleted here) or {@code MODIFIED} (in both, with different parameters)
 * @param changedFields the node's fields that differ, for {@code MODIFIED}; empty otherwise
 */
public record NodeDiffDTO(
        UUID identityId,
        String name,
        String nodeType,
        String change,
        List<String> changedFields
) {}
