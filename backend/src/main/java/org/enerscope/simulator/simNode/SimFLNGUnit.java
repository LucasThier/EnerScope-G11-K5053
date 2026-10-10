package org.enerscope.simulator.simNode;

import org.enerscope.node.model.liquefaction.FLNGUnit;
import org.enerscope.simulator.auxiliary.ISeaTerminal;
import org.enerscope.simulator.results.ResultPerNode;


public class SimFLNGUnit extends SimBaseLiquefactionPlant implements ISeaTerminal {
    protected int shipCapacity;
    protected int amountOfShip;

    public SimFLNGUnit(FLNGUnit flngUnit) {
        super(flngUnit, flngUnit.getMaxProcessingCapacity(), flngUnit.getMTPARatio(), flngUnit.getIntermediateStorage(), flngUnit.getGasConsumption());
        this.shipCapacity = flngUnit.getShipCapacity();
        this.amountOfShip = 0;
    }

    public void addBoat() {
        amountOfShip += 1;
    }

    public void restBoat() {
        amountOfShip -= 1;
    }

    public boolean shipAbleToDock() {
        return amountOfShip < shipCapacity;
    }
    @Override
    public ResultPerNode createResult() {
        ResultPerNode resultPerNode = new ResultPerNode(this.id, FLNGUnit.class.getSimpleName(), totalProduced, totalDeferred, maxPossibleProduced);
        resultPerNode.setExtra(totalDischarged);
        return resultPerNode;
    }

    @Override
    public void reset() {
        super.reset();
        this.amountOfShip = 0;
    }
}
