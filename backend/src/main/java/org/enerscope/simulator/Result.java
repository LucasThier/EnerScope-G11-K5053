package org.enerscope.simulator;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class Result {
    protected UUID id;

    private int year;

    private List<ResultPerNode> resultPerNodes;

    public Result() {

    }

    public void addAllResultPerNodes(List<ResultPerNode> resultPerNodes) {
        this.resultPerNodes.addAll(resultPerNodes);
    }

    public Result(int year) {
        this.year = year;
        this.resultPerNodes = new ArrayList<>();
    }
}
