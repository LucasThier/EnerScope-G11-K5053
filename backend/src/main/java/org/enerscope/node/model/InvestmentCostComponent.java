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
}