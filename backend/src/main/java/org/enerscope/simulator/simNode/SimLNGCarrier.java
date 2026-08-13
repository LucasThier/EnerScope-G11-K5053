package org.enerscope.simulator.simNode;

import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.simulator.FlagOfInactivity;

public class SimLNGCarrier extends SimBaseNode{
    private int exportFrequency;
    private float shipCapacity;
    private float fullLoadTime;
    private int timeToDestination;
    private SimSeaportTerminal simSeaportTerminal;
    private int timeOfDeparture;
    private float timeLoading;

    SimLNGCarrier(LNGCarrier lngCarrier){
        super(lngCarrier);
        this.exportFrequency = lngCarrier.getExportFrequency();
        this.shipCapacity = lngCarrier.getShipCapacity();
        this.fullLoadTime = lngCarrier.getFullLoadTime();
        this.timeToDestination = lngCarrier.getTimeToDestination();
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
                if(timeOfInactivity >= timeToDestination * 2){
                    active = true;
                }
            }
        }
    }

    @Override
    protected void activeAction(int time){


    }

}
