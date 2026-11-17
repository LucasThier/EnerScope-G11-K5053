package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for PipelineConnection node type.
 * Contains all fields needed to create a PipelineConnection.
 */
@Getter
@Setter
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
public class PipelineConnectionDTO extends BaseNodeDTO {

    // PipelineConnection-specific fields
    private Float transferCapacity;
    private Float outputPriority;
}