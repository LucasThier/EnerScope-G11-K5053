package org.enerscope.node.model.export;

import jakarta.persistence.*;
import lombok.*;
import org.enerscope.money.MoneyAmount;
import java.time.Instant;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeIdentity;
import org.enerscope.node.model.enums.NodeStateEnum;

import org.enerscope.node.model.NodeTypeData;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class SeaportTerminal extends ExportNode {

    @Column(name = "intermediate_storage")
    private float intermediateStorage;

    @Column(name = "port_depth")
    private float portDepth;

    @Column(name = "ship_capacity")
    private int shipCapacity;

    public SeaportTerminal(String name, NodeStateEnum state, Instant startupDate,
            int lifespanInMonths, MoneyAmount upkeepCosts,
            int maintenanceIntervalInDays, MoneyAmount operatingCosts,
            float wastePercentage, InvestmentCost investmentCost,
            NodeGraphData graphData, NodeIdentity identity, NodeTypeData type,
            float intermediateStorage, float portDepth, int shipCapacity) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                type, investmentCost, graphData, identity);
        this.intermediateStorage = intermediateStorage;
        this.portDepth = portDepth;
        this.shipCapacity = shipCapacity;
    }
}