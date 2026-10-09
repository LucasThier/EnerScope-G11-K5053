package org.enerscope.node.model.extraction;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
public class Well extends ExtractionNode {

    @Column(name = "max_collection_capacity")
    private float maxCollectionCapacity;

    @Column(name = "decline_curve")
    private float declineCurve;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private float gasRichness;

    @Column(name = "dtm_time")
    private int DTMTime;

    @Column(name = "surface")
    private float surface;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "DTMCost"))
    private MoneyAmount DTMCost;

    public Well(String name, NodeStateEnum state, Instant startupDate,
            int lifespanInMonths, MoneyAmount upkeepCosts,
            int maintenanceIntervalInDays, MoneyAmount operatingCosts,
            float wastePercentage, InvestmentCost investmentCost,
            NodeGraphData graphData, UUID identity, NodeTypeData type,
            float maxCollectionCapacity, float decline_curve, float gasRichness,
            int DTMTime, MoneyAmount DTMCost, float surface) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                investmentCost, graphData, identity, type);
        this.maxCollectionCapacity = maxCollectionCapacity;
        this.declineCurve = decline_curve;
        this.gasRichness = gasRichness;
        this.DTMTime = DTMTime;
        this.DTMCost = DTMCost;
        this.surface = surface;
    }
}