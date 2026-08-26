package org.enerscope.node.model;

import org.enerscope.common.BaseEntity;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.enums.CostBasisEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.enerscope.strategyCost.*;

@Entity
@Table(name = "investment_cost_component")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentCostComponent extends BaseEntity {

    @Column(name = "name", nullable = false, length = 320)
    private String name;

    @Column(name = "amount", nullable = false)
    private MoneyAmount amount;

    @Column(name = "costBasis")
    @Enumerated(EnumType.STRING)
    private CostBasisEnum costBasis;

    public MoneyAmount CalculateCost(BaseNode baseNode) {
        if(this.amount != null){
            switch (costBasis) {
                case PER_M:
                    return new Per_M().CalculateCost(baseNode, this.amount);
                case PER_KM:
                    return new Per_KM().CalculateCost(baseNode, this.amount);
                case PER_KM2:
                    return new Per_KM2().CalculateCost(baseNode, this.amount);
                case PER_CONECTIONS_TOTAL:
                    return new Per_Conections_Total().CalculateCost(baseNode, this.amount);
                default:
                    return MoneyAmount.of(0);
            }
        } else {
            throw new RuntimeException("Investment Component amount is empty");
        }
    }
}
