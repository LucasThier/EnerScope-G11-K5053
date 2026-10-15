package org.enerscope.node.dto;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for GatheringNetwork node type.
 * Contains all fields needed to create a GatheringNetwork.
 */
@Getter
@Setter
// Reset: the base class picks the subtype, this one binds plainly.
@JsonDeserialize(using = JsonDeserializer.None.class)
public class GatheringNetworkDTO extends BaseNodeDTO {

    private float maxTransportCapacity;
    private float length;
    private float lossPerMeter;
    private int connectedWells;

}