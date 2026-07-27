package org.enerscope.node.model.liquefaction;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeIdentity;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeStateEnum;

@NoArgsConstructor
@Getter
@Setter
@MappedSuperclass
public abstract class LiquefactionNode extends BaseNode {

    protected LiquefactionNode(String name, NodeStateEnum state, Instant startupDate,
            int lifespanInMonths, MoneyAmount upkeepCosts,
            int maintenanceIntervalInDays, MoneyAmount operatingCosts,
            float wastePercentage, InvestmentCost investmentCost,
            NodeGraphData graphData, NodeIdentity identity, NodeTypeData type) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                type, investmentCost, graphData, identity);
    }
}