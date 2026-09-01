package org.enerscope.node.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.enerscope.common.BaseEntity;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;

/**
 * Presentation data of a node. Holds two independent positions:
 * <ul>
 * <li>{@link GraphPosition} — where the node sits on the abstract diagram
 * canvas (x/y).</li>
 * <li>{@link GeographicalPosition} — where the node sits in the real world
 * (longitude/latitude), used by the map/globe view.</li>
 * </ul>
 * Both are nullable and independent: a node can have a diagram position without
 * a geographical one, and vice versa.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodeGraphData extends BaseEntity {

    @Embedded
    private GraphPosition graphPosition;

    @Embedded
    private GeographicalPosition geographicalPosition;
}
