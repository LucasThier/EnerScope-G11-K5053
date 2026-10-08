package org.enerscope.node.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Real-world position of a node, used by the map/globe view of the editor.
 * Field order follows MapLibre's {@code [longitude, latitude]} convention.
 * Both values are nullable: a node created in the diagram view may not have a
 * geographical position yet.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeographicalPosition {

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "latitude")
    private Double latitude;
}
