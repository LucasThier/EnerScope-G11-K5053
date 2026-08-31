package org.enerscope.simulator.simNode;

import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.simulator.FlagOfInactivity;

import static org.enerscope.simulator.FlagOfInactivity.OverLifeSpan;

public class SimLNGCarrier extends SimBaseNode{
    private int exportFrequency;
    private float shipCapacity;
    private float fullLoadTime;
    private int timeToDestination;
    private SimSeaportTerminal simSeaportTerminal;
    private boolean isInPort;
    private float amountToTake;
    private float amountInTank;

    public SimLNGCarrier(LNGCarrier lngCarrier){
        super(lngCarrier);
        this.exportFrequency = lngCarrier.getExportFrequency();
        this.shipCapacity = lngCarrier.getShipCapacity();
        this.fullLoadTime = lngCarrier.getFullLoadTime();
        this.timeToDestination = lngCarrier.getTimeToDestination();
        isInPort = false;
        amountToTake = shipCapacity / fullLoadTime;
        amountInTank = 0;
    }

    @Override
    protected void checkInactivity(int time){
        int timeOfInactivity = time - timeStartOfInactivity;
        switch (flagOfInactivity){
            case Mantainance: {
                if(timeOfInactivity >= maintenanceDuration){
                    active = true;
                }
            }
            case OverLifeSpan:{
                if(lifespanInMonths <= time / (24*30)){
                    if(timeOfInactivity >=  timeToDestination + exportFrequency){
                        active = true;
                        amountInTank = 0;
                    }
                }
            }
        }
    }

    @Override
    protected void activeAction(int time){
        if(!isInPort){
            if (simSeaportTerminal.shipAbleToDock()){
                simSeaportTerminal.addBoat();
            }
        } else {
            if (amountInTank >= shipCapacity) {
                flagOfInactivity = OverLifeSpan;
                simSeaportTerminal.restBoat();
            } else {
               float amountAvailable = simSeaportTerminal.getToDeliver().getAmount();
               float capacity = Math.min(shipCapacity - amountInTank , amountToTake);
               if (amountAvailable > capacity ){
                   simSeaportTerminal.deliver(capacity);
                   amountInTank += amountToTake;
               } else {
                   simSeaportTerminal.deliver(amountAvailable);
                   amountInTank += amountAvailable;
               }
            }
        }
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        simSeaportTerminal = (SimSeaportTerminal) simBaseNode;
    }
}
