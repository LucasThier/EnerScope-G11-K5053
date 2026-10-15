package org.enerscope.simulator.auxiliary;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ToDeliver {
    private float amount;
    private float contaminant;

    public ToDeliver(float amount, float contaminant){
        this.amount = amount;
        this.contaminant = contaminant;
    }

    public float clean(){
        float discharge = amount * (contaminant)/ 100;
        this.amount = amount - discharge;
        this.contaminant = 0;
        return discharge;
    }

    public void mix(List<ToDeliver> toDelivers) {
        // Plain loops rather than streams: this runs for every node on every
        // simulated hour, and the stream pipelines dominated the profile.
        double amountSum = 0;
        double contaminantSum = 0;
        for (int i = 0; i < toDelivers.size(); i++) {
            ToDeliver toDeliver = toDelivers.get(i);
            amountSum += toDeliver.getAmount();
            contaminantSum += contaminantMass(toDeliver);
        }
        mixSums(amountSum, contaminantSum);
    }

    /** The contaminant carried by a batch, as {@link #mix(List)} accumulates it. */
    public static double contaminantMass(ToDeliver toDeliver) {
        return toDeliver.getAmount() * (toDeliver.getContaminant() / 100);
    }

    /**
     * A new batch holding what {@link #mix(List)} would give mixing batches with
     * these accumulated sums into an empty one — the same float arithmetic, without
     * the list. Callers that take from many nodes accumulate as they go instead of
     * collecting every batch first, which used to allocate on every simulated hour.
     */
    public static ToDeliver ofMixture(double amountSum, double contaminantSum) {
        ToDeliver mixture = new ToDeliver(0, 0);
        mixture.mixSums(amountSum, contaminantSum);
        return mixture;
    }

    private void mixSums(double amountSum, double contaminantSum) {
        float totalAmountToAdd = (float) amountSum;

        float currentContaminantAmount = this.amount * (this.contaminant / 100);

        float addedContaminantAmount = (float) contaminantSum;

        float combinedAmount = this.amount + totalAmountToAdd;

        if (combinedAmount > 0) {
            this.contaminant = ((currentContaminantAmount + addedContaminantAmount) / combinedAmount) * 100;
        } else {
            this.contaminant = 0;
        }

        this.amount = combinedAmount;
    }
    public void mix(ToDeliver toDeliver) {
        float totalAmountToAdd = toDeliver.getAmount();

        float currentContaminantAmount = this.amount * (this.contaminant / 100);

        float addedContaminantAmount = (float) toDeliver.getAmount() * (toDeliver.getContaminant() / 100);

        float combinedAmount = this.amount + totalAmountToAdd;

        if (combinedAmount > 0) {
            this.contaminant = ((currentContaminantAmount + addedContaminantAmount) / combinedAmount) * 100;
        } else {
            this.contaminant = 0;
        }

        this.amount = combinedAmount;
    }

    public ToDeliver deliver(float amount){
        this.amount -= amount;
        return new ToDeliver(amount,this.contaminant);
    }

}
