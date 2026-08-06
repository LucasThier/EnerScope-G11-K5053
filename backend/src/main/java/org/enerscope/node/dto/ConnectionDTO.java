package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * Data Transfer Object for creating a connection between two nodes.
 */
@Getter
@Setter
public class ConnectionDTO {

    private UUID fromNodeId;
    private UUID toNodeId;
    private UUID identity;
}