package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.BaseNode;
import org.enerscope.simulator.FlagOfInactivity;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public abstract class SimBaseNode {
    protected UUID id;
    protected int maintenanceIntervalInDays;
    protected int maintenanceDuration;
    protected int lifespanInMonths;
    protected boolean active;
    protected int timeStartOfInactivity;
    protected int timeSinceLastMaintenance;
    protected FlagOfInactivity flagOfInactivity;
    protected float toDeliver;
    protected int lastSimulatedTime;


    SimBaseNode(BaseNode baseNode){
        this.id = baseNode.getId();
        this.maintenanceIntervalInDays = baseNode.getMaintenanceIntervalInDays();
        this.maintenanceDuration = baseNode.getMaintenanceDuration();
        this.lifespanInMonths = baseNode.getLifespanInMonths();
        this.active = true;
        this.timeStartOfInactivity = 0;
        this.toDeliver = 0;
        lastSimulatedTime = -1;
    }

    protected void checkLifeSpan(int time){
        if (time/24 >= lifespanInMonths*30){
            flagOfInactivity = FlagOfInactivity.OverLifeSpan;
            timeStartOfInactivity = time;
            active = false;
        }
    }

    protected void checkInactivity(int time){
        int timeOfInactivity = time - timeStartOfInactivity;
        switch (flagOfInactivity){
            case Mantainance: {
                if(timeOfInactivity >= maintenanceDuration){
                    active = true;
                }
            }
            case OverLifeSpan:{
                active = false;
            }
        }
    }

    protected void checkMaintenanceNeeded(int time){
        if (timeSinceLastMaintenance/24 >= maintenanceIntervalInDays){
            active = false;
            flagOfInactivity = FlagOfInactivity.Mantainance;
            timeStartOfInactivity = time;
        }
    }

    public void simulate(int time){
        before(time);
        if(active){
            activeAction(time);
        } else {
            inactiveAction(time);
        }
    }

    protected void before(int time){
        checkMaintenanceNeeded(time);
        lastSimulatedTime = time;
    }

    protected void activeAction(int time){checkLifeSpan(time);}
    protected void inactiveAction(int time){
        checkInactivity(time);
    }

    public void deliver(float amount){
        toDeliver -= amount;
    }

    public boolean readyToBeProcessed(int time) {
        return true;
    }

    public float takeEqualAmounts(List<? extends SimBaseNode> items, float capacity) {

        List<? extends SimBaseNode> itemsThatProduced = items.stream().filter(item ->item.getToDeliver() > 0).toList();

        if (itemsThatProduced.isEmpty() || capacity <= 0) {
            return 0;
        }

        float quantityToTake = capacity / itemsThatProduced.size();

        List<? extends SimBaseNode> withMore = itemsThatProduced.stream().filter(item -> item.getToDeliver() >= quantityToTake).toList();

        List<? extends SimBaseNode> withLess = itemsThatProduced.stream().filter(item -> item.getToDeliver() < quantityToTake).toList();

        if (withLess.isEmpty()) {
            withMore.forEach(item -> item.deliver(quantityToTake));
            return capacity;
        } else {
            float takenFromLess = calculateAndTakeAll(withLess);
            float remainingTaken = takeEqualAmounts(items, capacity - takenFromLess);
            return takenFromLess + remainingTaken;
        }
    }

    private float calculateToTake(List<? extends SimBaseNode> items) {
        return (float) items.stream().mapToDouble(item -> item.getToDeliver()).sum();
    }

    private void takeAll(List<? extends SimBaseNode> items) {
        items.forEach(item -> item.deliver(item.getToDeliver()));
    }

    public float calculateAndTakeAll(List<? extends SimBaseNode> items) {
        float quantityTaken = calculateToTake(items);
        takeAll(items);
        return quantityTaken;
    }
}
