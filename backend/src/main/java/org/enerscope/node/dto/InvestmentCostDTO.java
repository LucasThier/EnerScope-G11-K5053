package org.enerscope.node.dto;

import java.util.List;
import java.util.stream.Collectors;

import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.InvestmentCostComponent;

import lombok.Getter;
import lombok.Setter;

/**
 * InvestmentCostDTO
 */
@Getter
@Setter
public class InvestmentCostDTO {
    private List<InvestmentCostComponentDTO> components;

    public InvestmentCost DTOtoEntity() {
        List<InvestmentCostComponent> componentEntities = this.components.stream()
                .map(InvestmentCostComponentDTO::DTOtoEntity)
                .collect(Collectors.toList());

        InvestmentCost invCost = new InvestmentCost(componentEntities);
        return invCost;
    }
}