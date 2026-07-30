package org.enerscope.node.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * InvestmentCostDTO
 */
@Getter
@Setter
public class InvestmentCostDTO {
    private List<InvestmentCostComponentDTO> components;

}