package org.enerscope.node.dto;

import org.enerscope.node.model.InvestmentCostComponent;
import org.enerscope.node.model.NodeGraphData;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodeGraphDataDTO {

    private Double xPosition;
    private Double yPosition;
    private Double coordinates;

}
