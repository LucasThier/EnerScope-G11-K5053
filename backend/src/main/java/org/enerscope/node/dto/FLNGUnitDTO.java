package org.enerscope.node.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for FLNGUnit node type.
 * Contains all fields needed to create a FLNGUnit.
 */
@Getter
@Setter
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
public class FLNGUnitDTO extends BaseNodeDTO {

    // FLNGUnit-specific fields
    private Float maxProcessingCapacity;
    @JsonProperty("mtpaRatio")
    private Float MTPARatio;
    private Float intermediateStorage;
    private Float vesselDepth;
    private Float hiringCost;
    private Integer shipCapacity;
    private Float gasConsumption;
}