package org.enerscope.node.model.export;

import jakarta.persistence.MappedSuperclass;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeStateEnum;

@NoArgsConstructor
@Getter
@Setter
@MappedSuperclass
public abstract class ExportNode extends BaseNode {

    protected ExportNode(String name, NodeStateEnum state, Instant startupDate,
            int lifespanInMonths, MoneyAmount upkeepCosts,
            int maintenanceIntervalInDays, MoneyAmount operatingCosts,
            float wastePercentage, NodeTypeData type,
            InvestmentCost investmentCost, NodeGraphData graphData, UUID identity) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                type, investmentCost, graphData, identity);
    }
}