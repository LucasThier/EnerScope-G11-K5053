package org.enerscope.node.model.transportation;

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
public class PipelineConnection extends TransportNode {

    /** Transfer capacity */
    @Column(name = "transfer_capacity")
    private float transferCapacity;

    /** Output priority */
    @Column(name = "output_priority")
    private float outputPriority;

    public PipelineConnection(String name, NodeStateEnum state, Instant startupDate,
            int lifespanInMonths, MoneyAmount upkeepCosts,
            int maintenanceIntervalInDays, MoneyAmount operatingCosts,
            float wastePercentage, InvestmentCost investmentCost,
            NodeGraphData graphData, UUID identity, NodeTypeData type,
            float transferCapacity, float outputPriority) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                investmentCost, graphData, identity, type);
        this.transferCapacity = transferCapacity;
        this.outputPriority = outputPriority;
    }
}