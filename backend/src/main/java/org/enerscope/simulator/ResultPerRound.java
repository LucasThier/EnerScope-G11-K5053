package org.enerscope.simulator;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.export.LNGCarrier;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ResultPerRound {

    private List<ResultPerNode> resultPerNodes;

    public void addAllResultPerNodes(List<ResultPerNode> resultPerNodes){
        this.resultPerNodes.addAll(resultPerNodes);
    }

    public ResultPerRound(){
        this.resultPerNodes = new ArrayList<>();
    }

    public float amountProduced(){
        List<ResultPerNode> lngNodes = resultPerNodes.stream().filter(resultPerNode -> resultPerNode.getNodeClass() ==  LNGCarrier.class.getSimpleName()).toList();
        return (float) lngNodes.stream().mapToDouble(value -> value.getTotalProduced()).sum();
    }
}
