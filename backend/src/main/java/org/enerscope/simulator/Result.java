package org.enerscope.simulator;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "result")
public class Result {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    protected UUID id;

    @Column(name = "year")
    private int year;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "result_id", nullable = false)
    private List<ResultPerNode> resultPerNodes = new ArrayList<>();

    public Result() {

    }

    public void addAllResultPerNodes(List<ResultPerNode> resultPerNodes){
        this.resultPerNodes.addAll(resultPerNodes);
    }

    public Result(int year){
        this.year = year;
        this.resultPerNodes = new ArrayList<>();
    }
}
