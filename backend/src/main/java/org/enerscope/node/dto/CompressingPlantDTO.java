package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for CompressingPlant node type.
 * Contains all fields needed to create a CompressingPlant.
 */
@Getter
@Setter
public class CompressingPlantDTO extends BaseNodeDTO {

    // CompressingPlant-specific fields
    private Float maxCompressionCapacity;
    private Float processWaste;
    private Float gasConsumption;
}