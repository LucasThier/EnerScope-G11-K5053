package org.enerscope.node.dto;

import java.util.Map;
import java.util.UUID;

import org.enerscope.node.model.enums.NodeStateEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Full read model of a node for the editor's edit form: the common fields plus
 * the type-specific values in {@code attributes}, keyed by the same names the
 * create/edit payload uses. Lets the frontend pre-fill the edit form without
 * per-type DTOs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodeDetailDTO {

    private UUID id;
    private UUID identity;
    private String name;
    private NodeStateEnum state;
    private NodeTypeDataDTO type;
    private NodeGraphDataDTO graphData;

    // Common (base) editable fields.
    private Double upkeepCosts;
    private Double operatingCosts;
    private Integer lifespanInMonths;
    private Integer maintenanceIntervalInDays;
    private Double wastePercentage;

    // Type-specific fields keyed by the frontend field keys.
    private Map<String, Double> attributes;
}
