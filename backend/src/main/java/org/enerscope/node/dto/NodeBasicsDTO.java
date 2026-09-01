package org.enerscope.node.dto;

import org.enerscope.node.model.enums.NodeStateEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Partial update of a node's basic, type-independent fields (name / state).
 * Kept separate from the full node DTO so the editor can rename or re-state a
 * node without resending every type-specific field.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodeBasicsDTO {

    private String name;
    private NodeStateEnum state;
}
