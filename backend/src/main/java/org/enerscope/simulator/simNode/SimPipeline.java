package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.transportation.Pipeline;
import org.enerscope.simulator.auxiliary.ToDeliver;
import org.enerscope.simulator.auxiliary.TransitBatch;
import org.enerscope.simulator.results.ResultPerNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


@Getter
@Setter
public class SimPipeline extends SimBaseNode{
    private float maxFlowCapacity;
    private float loss;
    private List<SimBaseNode> nodesBefore;
    private float totalLost;

    private int travelTimeInHours;
    private List<TransitBatch> inTransitQueue;

    public SimPipeline(Pipeline pipeline){
        super(pipeline);
        this.maxFlowCapacity = pipeline.getMaxFlowCapacity();
        this.loss = pipeline.getLossPerKm() * pipeline.getLength();
        nodesBefore = new ArrayList<>();
        totalLost = 0;
        this.travelTimeInHours = (int) pipeline.getLength() / 70;
        this.inTransitQueue = new ArrayList<>();
    }

    @Override
    protected void activeAction(int time){
        ToDeliver totalArrived = new ToDeliver(0,0);
        // Loops instead of streams: this runs on every simulated hour.
        List<TransitBatch> arrivedTransitQueue = new ArrayList<>();
        double stillInTransit = 0;
        Iterator<TransitBatch> pending = inTransitQueue.iterator();
        while (pending.hasNext()) {
            TransitBatch aTransitBatch = pending.next();
            if (aTransitBatch.getTimeOfArrival() <= time) {
                arrivedTransitQueue.add(aTransitBatch);
                pending.remove();
            } else {
                stillInTransit += aTransitBatch.getToDeliver().getAmount();
            }
        }
        int a = Math.max(1,travelTimeInHours);
        float capacity = Math.min(maxFlowCapacity * a - toDeliver.getAmount() -
                (float) stillInTransit, maxFlowCapacity);
        float toGather = (float) availableIn(nodesBefore);
        maxPossibleProduced += maxFlowCapacity;

        TransitBatch transitBatch =new TransitBatch();
        transitBatch.setTimeOfArrival(time + travelTimeInHours);
        if (toGather >= capacity){
            transitBatch.setToDeliver(takeEqualAmounts(nodesBefore,capacity));
        } else {
            transitBatch.setToDeliver(calculateAndTakeAll(nodesBefore));
        }
        float batchAmount = transitBatch.getToDeliver().getAmount();
        float lost = batchAmount * (loss / 100);
        totalLost += lost;
        transitBatch.getToDeliver().setAmount(batchAmount - lost);
        inTransitQueue.add(transitBatch);

        arrivedTransitQueue.forEach(aTransitBatch -> totalArrived.mix(aTransitBatch.getToDeliver()));
        float newCapacity = maxFlowCapacity - toDeliver.getAmount();
        if(totalArrived.getAmount() <= newCapacity){
            toDeliver.mix(totalArrived);
        } else {
            ToDeliver toMix = new ToDeliver(newCapacity, totalArrived.getContaminant());
            ToDeliver remainingToDeliver = new ToDeliver(totalArrived.getAmount()-newCapacity,totalArrived.getContaminant());

            TransitBatch remaining = new TransitBatch(time);
            remaining.setToDeliver(remainingToDeliver);
            inTransitQueue.add(remaining);

            toDeliver.mix(toMix);
        }
    }
    @Override
    public boolean readyToBeProcessed(int time) {
        return nodesBefore.stream().allMatch(node -> node.getLastSimulatedTime() == time);
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        nodesBefore.add(simBaseNode);
    }

    @Override
    public ResultPerNode createResult() {
        ResultPerNode result = new ResultPerNode(this.id, Pipeline.class.getSimpleName(),totalProduced,totalDeferred,maxPossibleProduced);
        result.setExtra(totalLost);
        return result;
    }

    @Override
    public void reset() {
        super.reset();
        totalLost = 0;
    }
}
