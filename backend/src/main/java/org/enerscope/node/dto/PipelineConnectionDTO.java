package org.enerscope.node.dto;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for PipelineConnection node type.
 * Contains all fields needed to create a PipelineConnection.
 */
@Getter
@Setter
// Reset: the base class picks the subtype, this one binds plainly.
@JsonDeserialize(using = JsonDeserializer.None.class)
public class PipelineConnectionDTO extends BaseNodeDTO {

    // PipelineConnection-specific fields
    private Float transferCapacity;
    private Float outputPriority;
}