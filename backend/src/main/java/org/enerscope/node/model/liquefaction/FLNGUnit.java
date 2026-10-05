package org.enerscope.node.model.liquefaction;

import jakarta.persistence.*;
import lombok.*;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeGraphData;
import java.util.UUID;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeStateEnum;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "flng_unit")
@NoArgsConstructor
@AllArgsConstructor
public class FLNGUnit extends LiquefactionNode {

    @Column(name = "max_processing_capacity")
    private float maxProcessingCapacity;

    @Column(name = "mtpa_ratio")
    private float MTPARatio;

    @Column(name = "intermediate_storage")
    private float intermediateStorage;

    @Column(name = "vessel_depth")
    private float vesselDepth;

    private Float gasConsumption;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "hiringCost"))
    private MoneyAmount hiringCost;

    public FLNGUnit(String name, NodeStateEnum state, Instant startupDate,
            int lifespanInMonths, MoneyAmount upkeepCosts,
            int maintenanceIntervalInDays, MoneyAmount operatingCosts,
            float wastePercentage, InvestmentCost investmentCost,
            NodeGraphData graphData, UUID identity, NodeTypeData type,
            float maxProcessingCapacity, float MTPARatio,
            float intermediateStorage, float vesselDepth,
            MoneyAmount hiringCost) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                investmentCost, graphData, identity, type);
        this.maxProcessingCapacity = maxProcessingCapacity;
        this.MTPARatio = MTPARatio;
        this.intermediateStorage = intermediateStorage;
        this.vesselDepth = vesselDepth;
        this.hiringCost = hiringCost;
    }
}