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
@NoArgsConstructor
@AllArgsConstructor
public class GroundBasedLiquefactionPlant extends LiquefactionNode {

    @Column(name = "max_processing_capacity")
    private Float maxProcessingCapacity;

    @Column(name = "mtpa_ratio")
    private Float MTPARatio;

    @Column(name = "intermediate_storage")
    private Float intermediateStorage;

    @Column(name = "gas_consumption")
    private Float gasConsumption;

    public GroundBasedLiquefactionPlant(String name, NodeStateEnum state, Instant startupDate,
            int lifespanInMonths, MoneyAmount upkeepCosts,
            int maintenanceIntervalInDays, MoneyAmount operatingCosts,
            Float wastePercentage, InvestmentCost investmentCost,
            NodeGraphData graphData, UUID identity, NodeTypeData type,
            Float maxProcessingCapacity, Float MTPARatio,
            Float intermediateStorage, Float gasConsumption) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                investmentCost, graphData, identity, type);
        this.maxProcessingCapacity = maxProcessingCapacity;
        this.MTPARatio = MTPARatio;
        this.intermediateStorage = intermediateStorage;
        this.gasConsumption = gasConsumption;
    }
}