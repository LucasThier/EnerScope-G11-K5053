package org.enerscope.simulator;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ResultPerNode {
    private UUID nodeID;
    private String nodeClass;
    private float totalProduced;
    private float totalDeferred;
    private float maxPossibleProduced;
    private float extra;

    public ResultPerNode(UUID nodeID, String nodeClass, float totalProduced, float totalDeferred, float maxPossibleProduced) {
        this.nodeID = nodeID;
        this.nodeClass = nodeClass;
        this.totalProduced = totalProduced;
        this.totalDeferred = totalDeferred;
        this.maxPossibleProduced = maxPossibleProduced;
    }

}
