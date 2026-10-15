package org.enerscope.node.dto;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for TreatmentPlant node type.
 * Contains all fields needed to create a TreatmentPlant.
 */
@Getter
@Setter
// Reset: the base class picks the subtype, this one binds plainly.
@JsonDeserialize(using = JsonDeserializer.None.class)
public class TreatmentPlantDTO extends BaseNodeDTO {

    private float maxTreatmentCapacity;
    private float contaminantWaste;
    private float intermediateStorage;
    private float treatmentCost;
}