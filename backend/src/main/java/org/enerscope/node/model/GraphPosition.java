package org.enerscope.node.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Abstract position of a node on the diagram canvas (x/y in canvas units).
 * Used by the "diagram" view of the editor; it has no relation to the
 * real-world location (see {@link GeographicalPosition}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GraphPosition {

    @Column(name = "graph_x")
    private Double x;

    @Column(name = "graph_y")
    private Double y;
}
