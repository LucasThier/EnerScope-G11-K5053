package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for SeaportTerminal node type.
 * Contains all fields needed to create a SeaportTerminal.
 */
@Getter
@Setter
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
public class SeaportTerminalDTO extends BaseNodeDTO {

    // SeaportTerminal-specific fields
    private Float intermediateStorage;
    private Float portDepth;
    private int shipCapacity;
}