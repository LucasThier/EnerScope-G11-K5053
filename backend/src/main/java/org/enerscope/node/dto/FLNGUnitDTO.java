package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.money.MoneyAmount;

/**
 * Data Transfer Object for FLNGUnit node type.
 * Contains all fields needed to create a FLNGUnit.
 */
@Getter
@Setter
public class FLNGUnitDTO extends BaseNodeDTO {

    // FLNGUnit-specific fields
    private Float maxProcessingCapacity;
    private Float MTPARatio;
    private Float intermediateStorage;
    private Float vesselDepth;
    private MoneyAmount hiringCost;
}