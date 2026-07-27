package org.enerscope.node.model.extraction;

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
@NoArgsConstructor
@AllArgsConstructor
public class TreatmentPlant extends ExtractionNode {

    @Column(name = "max_treatment_capacity")
    private float maxTreatmentCapacity;

    @Column(name = "contaminant_waste")
    private float contaminantWaste;

    @Column(name = "intermediate_storage")
    private float intermediateStorage;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "treatmentCost"))
    private MoneyAmount treatmentCost;

    public TreatmentPlant(String name, NodeStateEnum state, Instant startupDate,
            int lifespanInMonths, MoneyAmount upkeepCosts,
            int maintenanceIntervalInDays, MoneyAmount operatingCosts,
            float wastePercentage, InvestmentCost investmentCost,
            NodeGraphData graphData, NodeIdentity identity, NodeTypeData type,
            float maxTreatmentCapacity, float contaminantWaste,
            float intermediateStorage, MoneyAmount treatmentCost) {
        super(name, state, startupDate, lifespanInMonths, upkeepCosts,
                maintenanceIntervalInDays, operatingCosts, wastePercentage,
                investmentCost, graphData, identity, type);
        this.maxTreatmentCapacity = maxTreatmentCapacity;
        this.contaminantWaste = contaminantWaste;
        this.intermediateStorage = intermediateStorage;
        this.treatmentCost = treatmentCost;
    }
}