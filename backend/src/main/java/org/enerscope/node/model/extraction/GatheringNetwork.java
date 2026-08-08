package org.enerscope.node.model.extraction;

import jakarta.persistence.*;
import lombok.*;
import org.enerscope.money.MoneyAmount;
import java.time.Instant;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeGraphData;
import java.util.UUID;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeStateEnum;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GatheringNetwork extends ExtractionNode {

    @Column(name = "max_transport_capacity")
    private float maxTransportCapacity;

    @Column(name = "length")
    private float length;

    @Column(name = "loss_per_meter")
    private float lossPerMeter;

    @Column(name = "connected_wells")
    private int connectedWells;

    public GatheringNetwork(String name, NodeStateEnum state, Instant startupDate,
            int lifespanInMonths, MoneyAmount upkeepCosts,
            int maintenanceIntervalInDays, MoneyAmount operatingCosts,
            float wastePercentage, InvestmentCost investmentCost,
            NodeGraphData graphData, UUID identity, NodeTypeData type,
            float maxTransportCapacity, float length, float lossPerMeter,
            int connectedWells) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                investmentCost, graphData, identity, type);
        this.maxTransportCapacity = maxTransportCapacity;
        this.length = length;
        this.lossPerMeter = lossPerMeter;
        this.connectedWells = connectedWells;
    }
}