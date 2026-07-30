package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for SeaportTerminal node type.
 * Contains all fields needed to create a SeaportTerminal.
 */
@Getter
@Setter
public class SeaportTerminalDTO extends BaseNodeDTO {

    // SeaportTerminal-specific fields
    private Float intermediateStorage;
    private Float portDepth;
    private int shipCapacity;
}