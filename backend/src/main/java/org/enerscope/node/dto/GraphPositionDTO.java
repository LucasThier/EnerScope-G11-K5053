package org.enerscope.node.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Abstract diagram-canvas position of a node (x/y in canvas units).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GraphPositionDTO {

    private Double x;
    private Double y;
}
