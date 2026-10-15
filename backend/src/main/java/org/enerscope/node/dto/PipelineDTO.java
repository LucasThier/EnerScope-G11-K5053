package org.enerscope.node.dto;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for Pipeline node type.
 * Contains all fields needed to create a Pipeline.
 */
@Getter
@Setter
// Reset: the base class picks the subtype, this one binds plainly.
@JsonDeserialize(using = JsonDeserializer.None.class)
public class PipelineDTO extends BaseNodeDTO {

    // Pipeline-specific fields
    private Float maxFlowCapacity;
    private Float length;
    private Float lossPerKm;
}