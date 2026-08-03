package org.enerscope.strategyCost;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.transportation.Pipeline;

public class Per_KM {
    public MoneyAmount CalculateCost(BaseNode baseNode, org.enerscope.money.MoneyAmount moneyAmount){
        if (baseNode instanceof Pipeline){
            return moneyAmount.multiply(((Pipeline) baseNode).getLength());
        } else {
            throw new RuntimeException("Wrong type of node");
        }
    }
}
