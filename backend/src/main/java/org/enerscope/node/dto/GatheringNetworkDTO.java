package org.enerscope.node.dto;

import org.enerscope.money.MoneyAmount;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for GatheringNetwork node type.
 * Contains all fields needed to create a GatheringNetwork.
 */
@Getter
@Setter
public class GatheringNetworkDTO extends BaseNodeDTO {

    private float maxTransportCapacity;
    private float length;
    private float lossPerMeter;
    private int connectedWells;

}