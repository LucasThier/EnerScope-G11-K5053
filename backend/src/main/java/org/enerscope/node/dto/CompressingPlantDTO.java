package org.enerscope.node.dto;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for CompressingPlant node type.
 * Contains all fields needed to create a CompressingPlant.
 */
@Getter
@Setter
// Reset: the base class picks the subtype, this one binds plainly.
@JsonDeserialize(using = JsonDeserializer.None.class)
public class CompressingPlantDTO extends BaseNodeDTO {

    // CompressingPlant-specific fields
    private Float maxCompressionCapacity;
    private Float processWaste;
    private Float gasConsumption;
}