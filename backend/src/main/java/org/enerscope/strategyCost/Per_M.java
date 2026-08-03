package org.enerscope.strategyCost;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.extraction.GatheringNetwork;

public class Per_M {
    public MoneyAmount CalculateCost(BaseNode baseNode, MoneyAmount moneyAmount){
        if (baseNode instanceof GatheringNetwork){
            return moneyAmount.multiply(((GatheringNetwork) baseNode).getLength());
        } else {
            throw new RuntimeException("Wrong type of node");
        }
    }
}
