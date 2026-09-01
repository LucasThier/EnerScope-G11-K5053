package org.enerscope.simulator;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Result {
    private int year;
    List<ResultPerNode> resultPerNodes;

    public void addAllResultPerNodes(List<ResultPerNode> resultPerNodes){
        this.resultPerNodes.addAll(resultPerNodes);
    }

    public Result(int year){
        this.year = year;
        this.resultPerNodes = new ArrayList<>();
    }
}
