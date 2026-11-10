package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for Pipeline node type.
 * Contains all fields needed to create a Pipeline.
 */
@Getter
@Setter
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
public class PipelineDTO extends BaseNodeDTO {

    // Pipeline-specific fields
    private Float maxFlowCapacity;
    private Float length;
    private Float lossPerKm;
}