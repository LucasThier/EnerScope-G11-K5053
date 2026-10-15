package org.enerscope.node.dto;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
// Reset: the base class picks the subtype, this one binds plainly.
@JsonDeserialize(using = JsonDeserializer.None.class)
public class IndustrialConsumptionDTO extends BaseNodeDTO{
    private Float consumption;
}
