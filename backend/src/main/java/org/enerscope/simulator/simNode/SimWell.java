package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.simulator.FlagOfInactivity;
import org.enerscope.simulator.ToDeliver;

@Getter
@Setter
public class SimWell extends SimBaseNode{
    private float maxCollectionCapacity;
    private float declineCurve;
    private float gasRichness;
    private int DTMTime;


    public SimWell(Well well){
        super(well);
        this.maxCollectionCapacity = well.getMaxCollectionCapacity();
        this.declineCurve = well.getDeclineCurve();
        this.gasRichness = well.getGasRichness();
        this.DTMTime = well.getDTMTime();
    }

    @Override
    protected void activeAction(int time){
        float totalDecline = calculateTotalDecline(time);

        if(totalDecline >= 100){
            flagOfInactivity = FlagOfInactivity.OverLifeSpan;
            timeStartOfInactivity = time;
            active = false;
            toDeliver = new ToDeliver(0,0);
        } else {
            toDeliver =new ToDeliver(maxCollectionCapacity * (100 - totalDecline) / 100,gasRichness);
        }

        checkLifeSpan(time);
    }

    @Override
    protected void inactiveAction(int time){
        toDeliver =  new ToDeliver(0,0);;
        checkInactivity(time);
    }

    private float calculateTotalDecline(int time){
        int year = time/(24*365);
        return declineCurve * year;
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
                if(timeOfInactivity >= DTMTime){
                    active = true;
                }
            }
        }
    }
}
