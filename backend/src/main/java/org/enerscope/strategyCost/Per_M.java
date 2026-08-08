package org.enerscope.strategyCost;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.extraction.GatheringNetwork;

public class Per_M {
    public MoneyAmount CalculateCost(BaseNode baseNode, MoneyAmount moneyAmount){
        Float length = ((GatheringNetwork) baseNode).getLength();
        if (baseNode instanceof GatheringNetwork && length != null){
            return moneyAmount.multiply(length);
        } else {
            throw new RuntimeException("Wrong type of node");
        }
    }
}
