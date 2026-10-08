package org.enerscope.node.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Read model of a connection (edge) as consumed by the editor.
 * {@code fromNodeId}/{@code toNodeId} reference the {@code id} of the endpoint
 * nodes within the same version (matching {@link DiagramNodeDTO#getId()}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiagramConnectionDTO {

    private UUID id;
    private UUID identity;
    private UUID fromNodeId;
    private UUID toNodeId;
}
