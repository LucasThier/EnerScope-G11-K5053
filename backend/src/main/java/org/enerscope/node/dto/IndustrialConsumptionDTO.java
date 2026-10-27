package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;

@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
@Getter
@Setter
public class IndustrialConsumptionDTO extends BaseNodeDTO{
    private Float consumption;
}
