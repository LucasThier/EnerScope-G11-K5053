package org.enerscope.strategyCost;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.transportation.Pipeline;

public class Per_KM2 {
    public MoneyAmount CalculateCost(BaseNode baseNode, org.enerscope.money.MoneyAmount moneyAmount){
        if (baseNode instanceof Well){
            return moneyAmount.multiply(((Well) baseNode).getSurface());
        } else {
            throw new RuntimeException("Wrong type of node");
        }
    }
}
