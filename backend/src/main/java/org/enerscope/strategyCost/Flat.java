package org.enerscope.strategyCost;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;

public class Flat {
    public MoneyAmount CalculateCost(BaseNode baseNode, MoneyAmount moneyAmount){
        return moneyAmount;
    }
}
