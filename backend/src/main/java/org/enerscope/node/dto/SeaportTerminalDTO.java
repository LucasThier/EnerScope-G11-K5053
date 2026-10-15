package org.enerscope.node.dto;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for SeaportTerminal node type.
 * Contains all fields needed to create a SeaportTerminal.
 */
@Getter
@Setter
// Reset: the base class picks the subtype, this one binds plainly.
@JsonDeserialize(using = JsonDeserializer.None.class)
public class SeaportTerminalDTO extends BaseNodeDTO {

    // SeaportTerminal-specific fields
    private Float intermediateStorage;
    private Float portDepth;
    private int shipCapacity;
}