package org.enerscope.simulation.dto;

import java.util.List;

/**
 * How a version's model differs from the comparison's baseline. Nodes are
 * matched by {@code identityId}; connections by the identities of the two nodes
 * they join, so the same link drawn separately in two branches is not reported
 * as one removed and one added.
 */
public record StructuralDiffDTO(
        int added,
        int removed,
        int modified,
        int unchanged,
        int connectionsAdded,
        int connectionsRemoved,
        List<NodeDiffDTO> nodes
) {}
