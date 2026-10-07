package org.enerscope.simulator;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "final_result")
@NoArgsConstructor
public class FinalResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    protected UUID id;
    @Column(name = "time")
    private int time;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResultPerNode> percentile90;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResultPerNode> percentile50;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResultPerNode> percentile10;

    @Column(name = "media_output")
    private float mediaOutput;

    public FinalResult(int time){
        this.time = time;
    }

}
