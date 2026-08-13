package org.enerscope.strategyCost;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.extraction.Well;

public class Per_KM2 {
    public MoneyAmount CalculateCost(BaseNode baseNode, org.enerscope.money.MoneyAmount moneyAmount){
        Float surface = ((Well) baseNode).getSurface();
        if (baseNode instanceof Well && surface != null){
            return moneyAmount.multiply(surface);
        } else {
            throw new RuntimeException("Wrong type of node");
        }
    }
}
