package org.enerscope.node.model.extraction;

import jakarta.persistence.MappedSuperclass;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeGraphData;
import java.util.UUID;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeStateEnum;

@NoArgsConstructor
@Getter
@Setter
@MappedSuperclass
public abstract class ExtractionNode extends BaseNode {

    protected ExtractionNode(String name, NodeStateEnum state, Instant startupDate,
            int lifespanInMonths, MoneyAmount upkeepCosts,
            int maintenanceIntervalInDays, MoneyAmount operatingCosts,
            float wastePercentage, InvestmentCost investmentCost,
            NodeGraphData graphData, UUID identity, NodeTypeData type) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                type, investmentCost, graphData, identity);
    }
}