package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.BaseNode;
import org.enerscope.simulator.FlagOfInactivity;
import org.enerscope.simulator.ResultPerNode;
import org.enerscope.simulator.ToDeliver;

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
    protected double measuredInput, measuredOutput, measuredExported, measuredLosses;
    protected long measuredOperatingHours, measuredEvents;
    protected float stepOutput;
    protected boolean calendarControlled;
    protected int operatingAgeHours = -1;


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
        if (calendarControlled) return;
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
        stepOutput = 0;
        float producedThisStep = 0;
        lastSimulatedTime = time;
        if(active){
            timeSinceLastMaintenance++;
            measuredOperatingHours++;
            activeAction(time);
            checkLifeSpan(time);
            producedThisStep = Math.max(0, stepOutput);
        } else {
            inactiveAction(time);
            producedThisStep = 0;
        }
        measuredOutput += producedThisStep;
        totalProduced = (float) measuredOutput;
        totalDeferred = getToDeliver().getAmount();
    }

    protected void before(int time){
        if (maintenanceIntervalInDays > 0) checkMaintenanceNeeded(time);
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

    public ToDeliver takeEqualAmounts(List<? extends SimBaseNode> items, float capacity) {
        List<? extends SimBaseNode> itemsThatProduced = items.stream().filter(item -> item.getToDeliver().getAmount() > 0).toList();

        if (itemsThatProduced.isEmpty() || capacity <= 0) {
            return new ToDeliver(0,0);
        }

        float quantityToTake = capacity / itemsThatProduced.size();

        List<? extends SimBaseNode> withMore = itemsThatProduced.stream().filter(item -> item.getToDeliver().getAmount() >= quantityToTake).toList();

        List<? extends SimBaseNode> withLess = itemsThatProduced.stream().filter(item -> item.getToDeliver().getAmount() < quantityToTake).toList();

        if (withLess.isEmpty()) {
            List<ToDeliver> toDelivers = withMore.stream().map(simBaseNode -> receive(simBaseNode, quantityToTake)).toList();
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
        List<ToDeliver> toDelivers = items.stream().map(item -> receive(item, item.getToDeliver().getAmount())).toList();
        ToDeliver toDeliver1 = new ToDeliver(0,0);
        toDeliver1.mix(toDelivers);
        return toDeliver1;
    }

    public void addPreviousNode(SimBaseNode simBaseNode){}

    protected ToDeliver receive(SimBaseNode source, float amount) {
        ToDeliver received = source.deliver(amount);
        measuredInput += received.getAmount();
        return received;
    }

    public abstract ResultPerNode createResult();
}
