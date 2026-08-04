package org.enerscope.strategyCost;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.transportation.Pipeline;

public class Per_KM {
    public MoneyAmount CalculateCost(BaseNode baseNode, org.enerscope.money.MoneyAmount moneyAmount){
        Float length = ((Pipeline) baseNode).getLength();
        if (baseNode instanceof Pipeline && length != null){
            return moneyAmount.multiply(length);
        } else {
            throw new RuntimeException("Wrong type of node");
        }
    }
}
