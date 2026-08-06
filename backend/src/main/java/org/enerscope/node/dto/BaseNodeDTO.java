package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

import org.enerscope.node.model.enums.NodeStateEnum;

/**
 * Base Data Transfer Object containing fields common to all node types.
 */
@Getter
@Setter
public class BaseNodeDTO {

    // Common fields from BaseNode
    private String name;
    // private String state; // Using String for simplicity, could use enum
    private Instant startupDate; // Using String for simplicity, could use Instant
    private Integer lifespanInMonths;
    private Float upkeepCosts;
    private Integer maintenanceIntervalInDays;
    private Float operatingCosts;
    private Float wastePercentage;
    private NodeStateEnum state;
    private InvestmentCostDTO investmentCost;
    private NodeGraphDataDTO graphData;
    private NodeTypeDataDTO type;
    private UUID identity;
}