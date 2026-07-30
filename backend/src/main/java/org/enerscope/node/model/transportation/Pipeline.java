package org.enerscope.node.model.transportation;

import jakarta.persistence.*;
import lombok.*;
import org.enerscope.money.MoneyAmount;
import java.time.Instant;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeIdentity;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeStateEnum;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Pipeline extends TransportNode {

    /** Maximum flow capacity */
    @Column(name = "max_flow_capacity")
    private float maxFlowCapacity;

    /** Length of the pipeline */
    @Column(name = "length")
    private float length;

    /** Loss per kilometer */
    @Column(name = "loss_per_km")
    private float lossPerKm;

    public Pipeline(String name, NodeStateEnum state, Instant startupDate,
            int lifespanInMonths, MoneyAmount upkeepCosts,
            int maintenanceIntervalInDays, MoneyAmount operatingCosts,
            float wastePercentage, InvestmentCost investmentCost,
            NodeGraphData graphData, NodeIdentity identity, NodeTypeData type,
            float maxFlowCapacity, float length, float lossPerKm) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                investmentCost, graphData, identity, type);
        this.maxFlowCapacity = maxFlowCapacity;
        this.length = length;
        this.lossPerKm = lossPerKm;
    }
}