package org.enerscope.simulator;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
public class ResultPerNode {
    protected UUID id;

    private UUID nodeID;
    private String nodeClass;
    private float totalProduced;
    private float totalDeferred;
    private float maxPossibleProduced;
    private float extra;

    public ResultPerNode(UUID nodeID, String nodeClass, float totalProduced, float totalDeferred,
            float maxPossibleProduced) {
        this.nodeID = nodeID;
        this.nodeClass = nodeClass;
        this.totalProduced = totalProduced;
        this.totalDeferred = totalDeferred;
        this.maxPossibleProduced = maxPossibleProduced;
    }

    public ResultPerNode() {

    }

}
