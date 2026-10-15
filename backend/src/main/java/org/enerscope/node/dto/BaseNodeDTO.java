package org.enerscope.node.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.dto.ConnectionDTO;
import org.enerscope.node.dto.WellDTO;
import org.enerscope.node.dto.TreatmentPlantDTO;
import org.enerscope.node.dto.GatheringNetworkDTO;
import org.enerscope.node.dto.PipelineDTO;
import org.enerscope.node.dto.CompressingPlantDTO;
import org.enerscope.node.dto.GroundBasedLiquefactionPlantDTO;
import org.enerscope.node.dto.FLNGUnitDTO;
import org.enerscope.node.dto.LNGCarrierDTO;
import org.enerscope.node.dto.SeaportTerminalDTO;

/**
 * Base Data Transfer Object containing fields common to all node types.
 */
@Getter
@Setter
// The concrete subtype is chosen by type.nodeType; see BaseNodeDTODeserializer.
@JsonDeserialize(using = BaseNodeDTODeserializer.class)
public class BaseNodeDTO {

    // Common fields from BaseNode
    private String name;
    // private String state; // Using String for simplicity, could use enum
    private Instant startupDate; // Using String for simplicity, could use Instant
    private Integer lifespanInMonths;
    private Float upkeepCosts;
    private Integer maintenanceIntervalInDays;
    private Integer maintenanceDuration;
    private Float operatingCosts;
    private Float wastePercentage;
    private NodeStateEnum state;
    private InvestmentCostDTO investmentCost;
    private NodeGraphDataDTO graphData;
    private NodeTypeDataDTO type;
    private UUID identity;
}