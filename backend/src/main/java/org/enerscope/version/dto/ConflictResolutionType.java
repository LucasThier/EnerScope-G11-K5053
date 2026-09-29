package org.enerscope.version.dto;

/** Decision passed to {@code PATCH /version/conflicts/{conflictId}/resolve/{decision}}. */
public enum ConflictResolutionType {
    /** Adopt the merged parent's data for this entity into the conflicting sibling. */
    ACCEPT,
    /** Keep the sibling's own data, recording its divergence from the new parent. */
    REJECT
}
