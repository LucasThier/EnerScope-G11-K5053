package org.enerscope.simulator.simNode;

import java.util.function.DoubleSupplier;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.probabilistic.ProbabilisticDistribution;
import org.enerscope.simulator.auxiliary.FlagOfInactivity;
import org.enerscope.simulator.results.ResultPerNode;
import org.enerscope.simulator.auxiliary.ToDeliver;

@Getter
@Setter
public class SimWell extends SimBaseNode{
    private float maxCollectionCapacity;
    private ProbabilisticDistribution declineCurve;
    private float gasRichness;
    private int DTMTime;
    private float acumDecline;
    private DoubleSupplier declineSampler;
    private int appliedDeclineYear;

    public void configureRandom(long seed) {
        declineSampler = declineCurve == null ? () -> 0 : declineCurve.sampler(seed);
    }

    public SimWell(Well well){
        super(well);
        this.maxCollectionCapacity = well.getMaxCollectionCapacity();
        this.declineCurve = well.getDeclineCurve();
        this.gasRichness = well.getGasRichness();
        this.DTMTime = well.getDTMTime();
        this.acumDecline = 0;
        configureRandom(0L);
    }

    @Override
    protected void activeAction(int time){
        int age = calendarControlled ? operatingAgeHours : time;
        int year = Math.max(0, age / 8760);
        while (appliedDeclineYear < year) {
            calculateTotalDecline();
            appliedDeclineYear++;
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
            measuredOperatingHours++;
            timeSinceLastMaintenance++;
            activeAction(time);
            checkLifeSpan(time);
            producedThisStep = Math.max(0, getToDeliver().getAmount());
        } else {
            inactiveAction(time);
            producedThisStep = 0;
        }
        totalProduced += producedThisStep;
        measuredOutput += producedThisStep;
        totalDeferred += producedThisStep;
    }

    @Override
    protected void inactiveAction(int time){
        toDeliver =  new ToDeliver(0,0);
        checkInactivity(time);
    }

    private void calculateTotalDecline(){
        double sample = declineSampler.getAsDouble();
        if (!Double.isFinite(sample))
            throw new IllegalArgumentException("Non-finite decline sample");
        acumDecline = (float) Math.min(100, acumDecline + Math.max(0, Math.min(100, sample)));
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
        appliedDeclineYear = 0;
        this.acumDecline = 0;
        configureRandom(0L);
    }
}
