package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for GatheringNetwork node type.
 * Contains all fields needed to create a GatheringNetwork.
 */
@Getter
@Setter
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
public class GatheringNetworkDTO extends BaseNodeDTO {

    private float maxTransportCapacity;
    private float length;
    private float lossPerMeter;
    private int connectedWells;

}