package org.enerscope.node.dto;

import java.util.UUID;

import org.enerscope.node.model.enums.NodeStateEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Read model of a node as consumed by the editor canvas/map. Carries the
 * fields needed to render and position a node:
 * <ul>
 * <li>{@code id} — the table primary key; this is what connections reference
 * within a version.</li>
 * <li>{@code identity} — the cross-version identity (the same real node keeps
 * this id across versions).</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiagramNodeDTO {

    private UUID id;
    private UUID identity;
    private String name;
    private NodeStateEnum state;
    private NodeTypeDataDTO type;
    private NodeGraphDataDTO graphData;
}
