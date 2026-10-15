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
            contaminantSum += toDeliver.getAmount() * (toDeliver.getContaminant() / 100);
        }
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
