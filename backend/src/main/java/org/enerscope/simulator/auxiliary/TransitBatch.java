package org.enerscope.simulator.auxiliary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransitBatch {
    private ToDeliver toDeliver;
    private int timeOfArrival;

    public TransitBatch(int time){
        this.timeOfArrival = time;
    }
}
