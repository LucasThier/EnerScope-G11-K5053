package org.enerscope.node.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.enerscope.common.BaseEntity;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.enums.NodeStateEnum;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@Inheritance(strategy = InheritanceType.JOINED)
@Entity
public abstract class BaseNode extends BaseEntity {

    @Column(nullable = false, length = 320)
    protected String name;

    @Column(name = "node_state", nullable = false)
    @Enumerated(EnumType.STRING)
    protected NodeStateEnum state;

    @Column(name = "startupDate")
    protected Instant startupDate;

    @Column(name = "lifespanInMonths")
    protected int lifespanInMonths;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "upKeepCosts"))
    protected MoneyAmount upkeepCosts;

    @Column(name = "maintenance_interval_in_days")
    protected int maintenanceIntervalInDays;

    @Column(name = "maintenanceDuration")
    protected int maintenanceDuration;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "operatingCosts"))
    protected MoneyAmount operatingCosts; // monthly

    @Column(name = "wastePercentage")
    protected float wastePercentage;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true) // Esta guardado junto al nodo en su repositorio
                                                               // ("entidad
                                                               // transient asociada"), en caso de necesitar acceder a
                                                               // esta clase por separado, crear nuevo repositorio y
                                                               // guardarlos por separado en el service
    @JoinColumn(name = "typeId", nullable = false)
    protected NodeTypeData type;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true) // Esta guardado junto al nodo en su repositorio
                                                               // ("entidad
                                                               // transient asociada"), en caso de necesitar acceder a
                                                               // esta clase por separado, crear nuevo repositorio y
                                                               // guardarlos por separado en el service
    @JoinColumn(name = "investmentCostId", nullable = false)
    protected InvestmentCost investmentCost;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true) // Esta guardado junto al nodo en su repositorio
                                                               // ("entidad
                                                               // transient asociada"), en caso de necesitar acceder a
                                                               // esta clase por separado, crear nuevo repositorio y
                                                               // guardarlos por separado en el service
    @JoinColumn(name = "graphDataId", nullable = false)
    protected NodeGraphData graphData;

    @Column
    protected UUID identityId;

    //
    /*
     * IDEA BASE, DESPUES SEGUIR
     * public MoneyAmount totalCostOfNode() {
     * MoneyAmount totalInvestment = investmentCost.getAmount();
     * MoneyAmount totalOperating = operatingCosts.multiply(lifespanInMonths);
     * int totalMaintenance = lifespanInMonths / (maintenanceIntervalInDays / 30);
     * MoneyAmount totalUpkeep = upkeepCosts.multiply(totalMaintenance);
     * return totalInvestment.add(totalOperating).add(totalUpkeep);
     * }
     */
    public boolean isOperational() {
        return state == NodeStateEnum.RUNNING && super.isActive();
    }

    public double RemainingLifespanPercentage() {
        if (startupDate == null || lifespanInMonths <= 0) {
            return 0.0;
        }

        long monthsElapsed = java.time.temporal.ChronoUnit.MONTHS.between(
                startupDate, Instant.now());
        double remainingMonths = lifespanInMonths - Math.max(0, monthsElapsed);
        return Math.max(0, Math.min(100, (remainingMonths / lifespanInMonths) * 100));
    }
}