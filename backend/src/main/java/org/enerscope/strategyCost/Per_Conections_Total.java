package org.enerscope.strategyCost;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.transportation.PipelineConnection;

public class Per_Conections_Total {
    public MoneyAmount CalculateCost(BaseNode baseNode, org.enerscope.money.MoneyAmount moneyAmount){
        if (baseNode instanceof GatheringNetwork){
            Integer number = ((GatheringNetwork) baseNode).getConnectedWells();
            if (number != null){
                return moneyAmount.multiply(number);
            } else {
                throw new RuntimeException("Wrong type of node");
            }
        } else if (baseNode instanceof PipelineConnection) {
            return moneyAmount;
        } else {
            throw new RuntimeException("Wrong type of node");
        }
    }
}
