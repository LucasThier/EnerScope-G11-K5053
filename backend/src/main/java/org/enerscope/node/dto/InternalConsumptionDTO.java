package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;

@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
@Getter
@Setter
public class InternalConsumptionDTO extends BaseNodeDTO{
    private Float consumptionSummer;
    private Float consumptionAutumn;
    private Float consumptionWinter;
    private Float consumptionSpring;
}
