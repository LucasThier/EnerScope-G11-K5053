package org.enerscope.simulator.results;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One simulation run of a version: the per-node results of the rounds picked as
 * percentiles, the mean delivered volume across rounds, and — snapshotted at run
 * time — the cost of the model that was simulated, so a run stays
 * self-consistent even if the version is edited afterwards.
 */
@Getter
@Setter
@Entity
@Table(name = "final_result")
@NoArgsConstructor
public class FinalResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    protected UUID id;
    /** Simulation horizon, in years. */
    @Column(name = "time")
    private int time;
    @Column(name = "rounds")
    private int rounds;
    @Column(name = "created_at")
    private Instant createdAt;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(name = "final_result_p90", joinColumns = @JoinColumn(name = "final_result_id"),
            inverseJoinColumns = @JoinColumn(name = "result_per_node_id"))
    private List<ResultPerNode> percentile90 = new ArrayList<>();
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(name = "final_result_p50", joinColumns = @JoinColumn(name = "final_result_id"),
            inverseJoinColumns = @JoinColumn(name = "result_per_node_id"))
    private List<ResultPerNode> percentile50 = new ArrayList<>();
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(name = "final_result_p10", joinColumns = @JoinColumn(name = "final_result_id"),
            inverseJoinColumns = @JoinColumn(name = "result_per_node_id"))
    private List<ResultPerNode> percentile10 = new ArrayList<>();

    /** Mean, across rounds, of the volume the chain delivered (exports plus domestic consumption). */
    @Column(name = "media_output")
    private float mediaOutput;

    @Column(name = "capex", precision = 19, scale = 2)
    private BigDecimal capex = BigDecimal.ZERO;
    @Column(name = "opex", precision = 19, scale = 2)
    private BigDecimal opex = BigDecimal.ZERO;
    @Column(name = "upkeep", precision = 19, scale = 2)
    private BigDecimal upkeep = BigDecimal.ZERO;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "final_result_id", nullable = false)
    private List<NodeCostResult> nodeCosts = new ArrayList<>();

    public FinalResult(int time){
        this.time = time;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public BigDecimal totalCost() {
        return capex.add(opex).add(upkeep);
    }
}
