package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.BaseNode;
import org.enerscope.simulator.auxiliary.FlagOfInactivity;
import org.enerscope.simulator.results.ResultPerNode;
import org.enerscope.simulator.auxiliary.ToDeliver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public abstract class  SimBaseNode {
    protected UUID id;
    protected int maintenanceIntervalInDays;
    protected int maintenanceDuration;
    protected int lifespanInMonths;
    protected boolean active;
    protected int timeStartOfInactivity;
    protected int timeSinceLastMaintenance;
    protected FlagOfInactivity flagOfInactivity;
    protected ToDeliver toDeliver;
    protected int lastSimulatedTime;
    protected float totalProduced;
    protected float totalDeferred;
    protected float maxPossibleProduced;


    SimBaseNode(BaseNode baseNode){
        this.id = baseNode.getId();
        this.maintenanceIntervalInDays = baseNode.getMaintenanceIntervalInDays();
        this.maintenanceDuration = baseNode.getMaintenanceDuration();
        this.lifespanInMonths = baseNode.getLifespanInMonths();
        this.active = true;
        this.timeStartOfInactivity = 0;
        this.toDeliver = new ToDeliver(0,0);
        lastSimulatedTime = -1;
        this.totalDeferred = 0;
        this.totalProduced = 0;
        this. maxPossibleProduced = 0;
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
            case Maintenance: {
                if(timeOfInactivity >= maintenanceDuration){
                    active = true;
                    timeSinceLastMaintenance = 0;
                }
                break;
            }
            case OverLifeSpan:{
                active = false;
                break;
            }
        }
    }

    protected void checkMaintenanceNeeded(int time){
        if (timeSinceLastMaintenance/24 >= maintenanceIntervalInDays){
            active = false;
            flagOfInactivity = FlagOfInactivity.Maintenance;
            timeStartOfInactivity = time;
        }
    }

    public void simulate(int time){
        before(time);
        float producedThisStep = 0;
        lastSimulatedTime = time;
        if(active){
            timeSinceLastMaintenance++;
            float amountBefore = getToDeliver().getAmount();
            activeAction(time);
            checkLifeSpan(time);
            producedThisStep = Math.max(0, getToDeliver().getAmount() - amountBefore);
        } else {
            inactiveAction(time);
            producedThisStep = 0;
        }
        totalProduced += producedThisStep;
        totalDeferred += producedThisStep;
    }

    protected void before(int time){
        checkMaintenanceNeeded(time);
    }

    protected void activeAction(int time){}
    protected void inactiveAction(int time){
        checkInactivity(time);
    }

    public ToDeliver deliver(float amount){
        totalDeferred -= amount;
        return toDeliver.deliver(amount);
    }

    public boolean readyToBeProcessed(int time) {
        return true;
    }

    /**
     * What the given nodes currently hold ready to deliver, summed. A plain loop
     * instead of a stream because it runs for every node on every simulated hour.
     */
    protected static double availableIn(List<? extends SimBaseNode> nodes) {
        double total = 0;
        for (int i = 0; i < nodes.size(); i++) {
            total += nodes.get(i).getToDeliver().getAmount();
        }
        return total;
    }

    // The two methods below are on the hot path too, so they use loops instead
    // of streams; the selection and the order of the deliveries are unchanged.
    public ToDeliver takeEqualAmounts(List<? extends SimBaseNode> items, float capacity) {
        List<SimBaseNode> itemsThatProduced = new ArrayList<>(items.size());
        for (SimBaseNode item : items) {
            if (item.getToDeliver().getAmount() > 0) {
                itemsThatProduced.add(item);
            }
        }

        if (itemsThatProduced.isEmpty() || capacity <= 0) {
            return new ToDeliver(0,0);
        }

        float quantityToTake = capacity / itemsThatProduced.size();

        List<SimBaseNode> withMore = new ArrayList<>(itemsThatProduced.size());
        List<SimBaseNode> withLess = new ArrayList<>(itemsThatProduced.size());
        for (SimBaseNode item : itemsThatProduced) {
            float amount = item.getToDeliver().getAmount();
            if (amount >= quantityToTake) {
                withMore.add(item);
            } else if (amount < quantityToTake) {
                withLess.add(item);
            }
        }

        if (withLess.isEmpty()) {
            List<ToDeliver> toDelivers = new ArrayList<>(withMore.size());
            for (SimBaseNode simBaseNode : withMore) {
                toDelivers.add(simBaseNode.deliver(quantityToTake));
            }
            ToDeliver toDeliver1 = new ToDeliver(0,0);
            toDeliver1.mix(toDelivers);
            return toDeliver1;
        } else {
            ToDeliver takenFromLess = calculateAndTakeAll(withLess);
            ToDeliver remainingTaken = takeEqualAmounts(withMore, capacity - takenFromLess.getAmount());
            takenFromLess.mix(remainingTaken);
            return takenFromLess;
        }
    }

    public ToDeliver calculateAndTakeAll(List<? extends SimBaseNode> items) {
        List<ToDeliver> toDelivers = new ArrayList<>(items.size());
        for (SimBaseNode item : items) {
            toDelivers.add(item.deliver(item.getToDeliver().getAmount()));
        }
        ToDeliver toDeliver1 = new ToDeliver(0,0);
        toDeliver1.mix(toDelivers);
        return toDeliver1;
    }

    public void addPreviousNode(SimBaseNode simBaseNode){}

    public abstract ResultPerNode createResult();
    public void reset(){
        lastSimulatedTime = -1;
        this.totalDeferred = 0;
        this.totalProduced = 0;
        this. maxPossibleProduced = 0;
        this.active = true;
        this.timeStartOfInactivity = 0;
        this.toDeliver = new ToDeliver(0,0);
    }
}
