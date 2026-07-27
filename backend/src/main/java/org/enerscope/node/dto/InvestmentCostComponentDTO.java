package org.enerscope.node.dto;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.InvestmentCostComponent;
import org.enerscope.node.model.enums.CostBasisEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * InvestmentCostComponentDTO
 */
@Getter
@Setter
public class InvestmentCostComponentDTO {
    private String name;
    private MoneyAmount amount;
    private CostBasisEnum costBasis;
}