package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.liquefaction.GroundBasedLiquefactionPlant;
import org.enerscope.simulator.results.ResultPerNode;
import org.enerscope.simulator.auxiliary.ToDeliver;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SimGroundBasedLiquefactionPlant extends SimBaseLiquefactionPlant{
    public SimGroundBasedLiquefactionPlant(GroundBasedLiquefactionPlant plant) {
        super(plant, plant.getMaxProcessingCapacity(), plant.getMTPARatio(), plant.getIntermediateStorage(), plant.getGasConsumption());
    }

    @Override
    public ResultPerNode createResult() {
        ResultPerNode resultPerNode = new ResultPerNode(this.id, GroundBasedLiquefactionPlant.class.getSimpleName(), totalProduced, totalDeferred, maxPossibleProduced);
        resultPerNode.setExtra(totalDischarged);
        return resultPerNode;
    }
}
