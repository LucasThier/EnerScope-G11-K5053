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
@Table(name = "compressing_plant")
@NoArgsConstructor
@AllArgsConstructor
public class CompressingPlant extends TransportNode {

    @Column(name = "max_compression_capacity")
    private float maxCompressionCapacity;

    @Column(name = "process_waste")
    private float processWaste;

    @Column(name = "gas_consumption")
    private float gasConsumption;

    public CompressingPlant(String name, NodeStateEnum state, Instant startupDate,
            int lifespanInMonths, MoneyAmount upkeepCosts,
            int maintenanceIntervalInDays, MoneyAmount operatingCosts,
            float wastePercentage, InvestmentCost investmentCost,
            NodeGraphData graphData, NodeIdentity identity, NodeTypeData type,
            float maxCompressionCapacity, float processWaste,
            float gasConsumption) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                investmentCost, graphData, identity, type);
        this.maxCompressionCapacity = maxCompressionCapacity;
        this.processWaste = processWaste;
        this.gasConsumption = gasConsumption;
    }
}