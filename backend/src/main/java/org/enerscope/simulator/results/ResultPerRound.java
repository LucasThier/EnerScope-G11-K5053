package org.enerscope.simulator.results;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.export.LNGCarrier;

import java.util.ArrayList;
import java.util.List;


@Getter
@Setter
public class ResultPerRound {

    @Column(name = "year")
    private int year;

    // Foreign key on the child table (result_per_node.result_id, see V8). Without
    // the @JoinColumn, JPA falls back to a join table (result_result_per_nodes)
    // that no migration creates.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "result_id", nullable = false)
    private List<ResultPerNode> resultPerNodes;


    public void addAllResultPerNodes(List<ResultPerNode> resultPerNodes){
        this.resultPerNodes.addAll(resultPerNodes);
    }

    public ResultPerRound(){
        this.resultPerNodes = new ArrayList<>();
    }

    public float amountProduced(){
        List<ResultPerNode> lngNodes = resultPerNodes.stream().filter(resultPerNode -> resultPerNode.getNodeClass() == LNGCarrier.class.getSimpleName()).toList();
        return (float) lngNodes.stream().mapToDouble(value -> value.getTotalProduced()).sum();
    }
}
