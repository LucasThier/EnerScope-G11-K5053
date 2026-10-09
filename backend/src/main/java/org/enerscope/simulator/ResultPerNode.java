package org.enerscope.simulator;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "result_per_node")
public class ResultPerNode {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    protected UUID id;

    // Named explicitly: the naming strategy only inserts an underscore before an
    // upper-case letter that is followed by a lower-case one, so "nodeID" would
    // end up as the column "nodeid" instead of "node_id" (see V8).
    @Column(name = "node_id")
    private UUID nodeID;
    @Column(name = "nodeClass", length = 100)
    private String nodeClass;
    @Column(name = "totalProduced")
    private float totalProduced;
    @Column(name = "totalDeferred")
    private float totalDeferred;
    @Column(name = "maxPossibleProduced")
    private float maxPossibleProduced;
    @Column(name = "extra")
    private float extra;

    public ResultPerNode(UUID nodeID, String nodeClass, float totalProduced, float totalDeferred, float maxPossibleProduced) {
        this.nodeID = nodeID;
        this.nodeClass = nodeClass;
        this.totalProduced = totalProduced;
        this.totalDeferred = totalDeferred;
        this.maxPossibleProduced = maxPossibleProduced;
    }

    public ResultPerNode() {

    }

}
