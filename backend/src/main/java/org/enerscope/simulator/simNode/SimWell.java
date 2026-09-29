package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.probabilistic.ConstantValue;
import org.enerscope.probabilistic.ProbabilisticDistribution;
import org.enerscope.simulator.FlagOfInactivity;
import org.enerscope.simulator.ResultPerNode;
import org.enerscope.simulator.ToDeliver;

@Getter
@Setter
public class SimWell extends SimBaseNode{
    private float maxCollectionCapacity;
    private ProbabilisticDistribution declineCurve;
    private float gasRichness;
    private int DTMTime;
    private float acumDecline;


    public SimWell(Well well){
        super(well);
        this.maxCollectionCapacity = well.getMaxCollectionCapacity();
        this.declineCurve = well.getDeclineCurve();
        this.gasRichness = well.getGasRichness();
        this.DTMTime = well.getDTMTime();
        this.acumDecline = 0;
    }

    @Override
    protected void activeAction(int time){
        if(time > 0 && time % (365*24) == 0){
            calculateTotalDecline();
        }

        if(acumDecline >= 100){
            flagOfInactivity = FlagOfInactivity.OverLifeSpan;
            timeStartOfInactivity = time;
            active = false;
            toDeliver = new ToDeliver(0,0);
        } else {
            float produced = maxCollectionCapacity * (100 - acumDecline)/ 100;
            maxPossibleProduced += produced;
            toDeliver =new ToDeliver(produced,gasRichness);
        }

    }

    @Override
    public void simulate(int time){
        before(time);
        float producedThisStep = 0;
        lastSimulatedTime = time;
        if(active){
            timeSinceLastMaintenance++;
            activeAction(time);
            checkLifeSpan(time);
            producedThisStep = Math.max(0, getToDeliver().getAmount());
        } else {
            inactiveAction(time);
            producedThisStep = 0;
        }
        totalProduced += producedThisStep;
        totalDeferred += producedThisStep;
    }

    @Override
    protected void inactiveAction(int time){
        toDeliver =  new ToDeliver(0,0);;
        checkInactivity(time);
    }

    private void calculateTotalDecline(){
        acumDecline += declineCurve.generateValue();
    }

    @Override
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
                if(timeOfInactivity >= DTMTime){
                    active = true;
                }
                break;
            }
        }
    }
    @Override
    public ResultPerNode createResult() {
        return new ResultPerNode(this.id, Well.class.getSimpleName(),totalProduced,totalDeferred,maxPossibleProduced);
    }

    @Override
    public void reset() {
        super.reset();
        this.acumDecline = 0;
    }
}
