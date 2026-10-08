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
@Table(name = "internal_consumption")
public class InternalConsumption extends ExportNode{
    @Column(name = "consumption_summer")
    private float consumptionSummer;

    @Column(name = "consumption_autumn")
    private float consumptionAutumn;

    @Column(name = "consumption_winter")
    private float consumptionWinter;

    @Column(name = "consumption_spring")
    private float consumptionSpring;

    public InternalConsumption(String name, NodeStateEnum state, Instant startupDate,
                               int lifespanInMonths, MoneyAmount upkeepCosts,
                               int maintenanceIntervalInDays, MoneyAmount operatingCosts,
                               float wastePercentage, InvestmentCost investmentCost,
                               NodeGraphData graphData, UUID identity, NodeTypeData type,
                               float consumptionSummer, float consumptionAutumn,
                               float consumptionWinter, float consumptionSpring) {

        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                type, investmentCost, graphData, identity);

        this.consumptionSummer = consumptionSummer;
        this.consumptionAutumn = consumptionAutumn;
        this.consumptionWinter = consumptionWinter;
        this.consumptionSpring = consumptionSpring;
    }
}
