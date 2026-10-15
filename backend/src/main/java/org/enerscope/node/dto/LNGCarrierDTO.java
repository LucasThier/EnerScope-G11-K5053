package org.enerscope.node.dto;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for LNGCarrier node type.
 * Contains all fields needed to create a LNGCarrier.
 */
@Getter
@Setter
// Reset: the base class picks the subtype, this one binds plainly.
@JsonDeserialize(using = JsonDeserializer.None.class)
public class LNGCarrierDTO extends BaseNodeDTO {

    // LNGCarrier-specific fields
    private int exportFrequency;
    private Float shipCapacity;
    private Float fullLoadTime;
    private Float hiringCost;
    private int timeToDestination;
}