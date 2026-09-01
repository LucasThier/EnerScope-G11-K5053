package org.enerscope.node.dto;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Full diagram of a version: the set of nodes and connections the editor
 * renders on the canvas / map. This is a flat, serialization-safe read model
 * built from a {@code Version}'s snapshot, so the API never leaks lazy JPA
 * associations.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiagramDTO {

    private UUID versionId;
    private List<DiagramNodeDTO> nodes;
    private List<DiagramConnectionDTO> connections;
}
