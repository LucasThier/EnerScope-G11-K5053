package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.money.MoneyAmount;

/**
 * Data Transfer Object for LNGCarrier node type.
 * Contains all fields needed to create a LNGCarrier.
 */
@Getter
@Setter
public class LNGCarrierDTO extends BaseNodeDTO {

    // LNGCarrier-specific fields
    private int exportFrequency;
    private Float shipCapacity;
    private Float fullLoadTime;
    private MoneyAmount hiringCost;
    private int timeToDestination;
}