package org.enerscope.node.dto;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for GroundBasedLiquefactionPlant node type.
 * Contains all fields needed to create a GroundBasedLiquefactionPlant.
 */
@Getter
@Setter
// Reset: the base class picks the subtype, this one binds plainly.
@JsonDeserialize(using = JsonDeserializer.None.class)
public class GroundBasedLiquefactionPlantDTO extends BaseNodeDTO {

    // GroundBasedLiquefactionPlant-specific fields
    private Float maxProcessingCapacity;
    @JsonProperty("mtpaRatio")
    private Float MTPARatio;
    private Float intermediateStorage;
    private Float gasConsumption;
}