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

    /**
     * Starts a maintenance stop when the interval has elapsed. Only an active
     * node can start one: this runs before every simulated hour, and while the
     * node was already stopped the elapsed counter (which only resets when the
     * stop ends) still met the interval, so the stop restarted every hour and
     * never ended — any node with a maintenance duration above zero stayed down
     * from its first maintenance on. It also kept a node past its lifespan from
     * being "revived" by a maintenance stop.
     */
    protected void checkMaintenanceNeeded(int time){
        if (active && timeSinceLastMaintenance/24 >= maintenanceIntervalInDays){
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
        // Fast path for the common case — every node with something to give has
        // at least its equal share — which takes that share from each, in list
        // order, exactly as the general path below does, without its lists.
        int producers = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getToDeliver().getAmount() > 0) {
                producers++;
            }
        }
        if (producers == 0 || capacity <= 0) {
            return new ToDeliver(0,0);
        }
        float share = capacity / producers;
        boolean someHaveLess = false;
        for (int i = 0; i < items.size() && !someHaveLess; i++) {
            float amount = items.get(i).getToDeliver().getAmount();
            someHaveLess = amount > 0 && amount < share;
        }
        if (!someHaveLess) {
            double amountSum = 0;
            double contaminantSum = 0;
            for (int i = 0; i < items.size(); i++) {
                SimBaseNode item = items.get(i);
                if (item.getToDeliver().getAmount() > 0) {
                    ToDeliver taken = item.deliver(share);
                    amountSum += taken.getAmount();
                    contaminantSum += ToDeliver.contaminantMass(taken);
                }
            }
            return ToDeliver.ofMixture(amountSum, contaminantSum);
        }

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
            double amountSum = 0;
            double contaminantSum = 0;
            for (SimBaseNode simBaseNode : withMore) {
                ToDeliver taken = simBaseNode.deliver(quantityToTake);
                amountSum += taken.getAmount();
                contaminantSum += ToDeliver.contaminantMass(taken);
            }
            return ToDeliver.ofMixture(amountSum, contaminantSum);
        } else {
            ToDeliver takenFromLess = calculateAndTakeAll(withLess);
            ToDeliver remainingTaken = takeEqualAmounts(withMore, capacity - takenFromLess.getAmount());
            takenFromLess.mix(remainingTaken);
            return takenFromLess;
        }
    }

    public ToDeliver calculateAndTakeAll(List<? extends SimBaseNode> items) {
        double amountSum = 0;
        double contaminantSum = 0;
        for (int i = 0; i < items.size(); i++) {
            SimBaseNode item = items.get(i);
            float available = item.getToDeliver().getAmount();
            if (available == 0) {
                // Delivering nothing changes nothing and adds nothing to the
                // mixture, so the call (and the batch it allocates) is skipped.
                continue;
            }
            ToDeliver taken = item.deliver(available);
            amountSum += taken.getAmount();
            contaminantSum += ToDeliver.contaminantMass(taken);
        }
        return ToDeliver.ofMixture(amountSum, contaminantSum);
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
