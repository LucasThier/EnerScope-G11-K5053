package org.enerscope.simulator.auxiliary;

public interface ISeaTerminal {
    void addBoat();
    void restBoat();
    boolean shipAbleToDock();
    ToDeliver getToDeliver();
    ToDeliver deliver(float amount);
}
