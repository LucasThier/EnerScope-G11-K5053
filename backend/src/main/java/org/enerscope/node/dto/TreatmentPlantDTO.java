package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for TreatmentPlant node type.
 * Contains all fields needed to create a TreatmentPlant.
 */
@Getter
@Setter
public class TreatmentPlantDTO extends BaseNodeDTO {

    private float maxTreatmentCapacity;
    private float contaminantWaste;
    private float intermediateStorage;
    private float treatmentCost;
}