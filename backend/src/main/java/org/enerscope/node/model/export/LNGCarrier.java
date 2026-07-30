package org.enerscope.node.model.export;

import jakarta.persistence.*;
import lombok.*;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeIdentity;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeStateEnum;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "lng_carrier")
public class LNGCarrier extends ExportNode {

    @Column(name = "export_frequency")
    private int exportFrequency;

    @Column(name = "ship_capacity")
    private float shipCapacity;

    @Column(name = "full_load_time")
    private float fullLoadTime;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "hiringCost"))
    private MoneyAmount hiringCost;

    @Column(name = "time_to_destination")
    private int timeToDestination;

    public LNGCarrier(String name, NodeStateEnum state, Instant startupDate,
            int lifespanInMonths, MoneyAmount upkeepCosts,
            int maintenanceIntervalInDays, MoneyAmount operatingCosts,
            float wastePercentage, InvestmentCost investmentCost,
            NodeGraphData graphData, NodeIdentity identity, NodeTypeData type,
            int exportFrequency, float shipCapacity, float fullLoadTime,
            MoneyAmount hiringCost, int timeToDestination) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                type, investmentCost, graphData, identity);
        this.exportFrequency = exportFrequency;
        this.shipCapacity = shipCapacity;
        this.fullLoadTime = fullLoadTime;
        this.hiringCost = hiringCost;
        this.timeToDestination = timeToDestination;
    }
}