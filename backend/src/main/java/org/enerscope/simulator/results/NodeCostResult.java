package org.enerscope.simulator.results;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The cost of one node over a run's horizon, as it was when the run happened.
 * The node's name, type and state are copied rather than referenced so the run
 * still reads correctly after the node is renamed, edited or deleted — which is
 * also why {@code node_id} carries no foreign key.
 */
@Getter
@Setter
@Entity
@Table(name = "node_cost_result")
@NoArgsConstructor
public class NodeCostResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;
    @Column(name = "identity_id")
    private UUID identityId;
    @Column(name = "node_name", nullable = false, length = 320)
    private String nodeName;
    @Column(name = "node_type", length = 50)
    private String nodeType;
    @Column(name = "vertical", length = 30)
    private String vertical;
    @Column(name = "node_state", length = 20)
    private String nodeState;

    @Column(name = "capex", precision = 19, scale = 2, nullable = false)
    private BigDecimal capex = BigDecimal.ZERO;
    @Column(name = "opex", precision = 19, scale = 2, nullable = false)
    private BigDecimal opex = BigDecimal.ZERO;
    @Column(name = "upkeep", precision = 19, scale = 2, nullable = false)
    private BigDecimal upkeep = BigDecimal.ZERO;

    public NodeCostResult(UUID nodeId, UUID identityId, String nodeName, String nodeType, String vertical,
                          String nodeState, BigDecimal capex, BigDecimal opex, BigDecimal upkeep) {
        this.nodeId = nodeId;
        this.identityId = identityId;
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.vertical = vertical;
        this.nodeState = nodeState;
        this.capex = capex;
        this.opex = opex;
        this.upkeep = upkeep;
    }

    public BigDecimal total() {
        return capex.add(opex).add(upkeep);
    }
}
