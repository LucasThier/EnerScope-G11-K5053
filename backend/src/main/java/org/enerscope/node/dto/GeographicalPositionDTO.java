package org.enerscope.node.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Real-world position of a node ({@code [longitude, latitude]}), used by the
 * map/globe view.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeographicalPositionDTO {

    private Double longitude;
    private Double latitude;
}
