package org.enerscope.node.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for GroundBasedLiquefactionPlant node type.
 * Contains all fields needed to create a GroundBasedLiquefactionPlant.
 */
@Getter
@Setter
public class GroundBasedLiquefactionPlantDTO extends BaseNodeDTO {

    // GroundBasedLiquefactionPlant-specific fields
    private Float maxProcessingCapacity;
    @JsonProperty("mtpaRatio")
    private Float MTPARatio;
    private Float intermediateStorage;
    private Float gasConsumption;
}