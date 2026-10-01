package org.enerscope.node.model.export;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeStateEnum;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "industrial_consumption")
public class IndustrialConsumption extends ExportNode{
    @Column(name = "consumption")
    private float consumption;

    public IndustrialConsumption(String name, NodeStateEnum state, Instant startupDate,
                                 int lifespanInMonths, MoneyAmount upkeepCosts,
                                 int maintenanceIntervalInDays, MoneyAmount operatingCosts,
                                 float wastePercentage, InvestmentCost investmentCost,
                                 NodeGraphData graphData, UUID identity, NodeTypeData type,
                                 float consumption) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                type, investmentCost, graphData, identity);
       this.consumption = consumption;
    }
}
