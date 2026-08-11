package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.BaseNode;
import org.enerscope.simulator.FlagOfInactivity;

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


    SimBaseNode(BaseNode baseNode){
        this.id = baseNode.getId();
        this.maintenanceIntervalInDays = baseNode.getMaintenanceIntervalInDays();
        this.maintenanceDuration = baseNode.getMaintenanceDuration();
        this.lifespanInMonths = baseNode.getLifespanInMonths();
        this.active = true;
        this.timeStartOfInactivity = 0;
        this.toDeliver = 0;
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

    public void simulateTime(int time){
        before(time);
        if(active){
            activeAction(time);
        } else {
            inactiveAction(time);
        }
    }

    protected void before(int time){
        checkMaintenanceNeeded(time);
    }

    protected void activeAction(int time){

    }
    protected void inactiveAction(int time){
        checkInactivity(time);
    }

    public void deliver(float amount){
        toDeliver -= amount;
    }

    public boolean readyToBeProcessed() {
        return true;
    }
}
