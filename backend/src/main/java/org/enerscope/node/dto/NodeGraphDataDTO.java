package org.enerscope.node.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Presentation data of a node: its position on the diagram canvas
 * ({@link GraphPositionDTO}) and, optionally, its real-world position
 * ({@link GeographicalPositionDTO}). Both are independent and nullable.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodeGraphDataDTO {

    private GraphPositionDTO graphPosition;
    private GeographicalPositionDTO geographicalPosition;
}
