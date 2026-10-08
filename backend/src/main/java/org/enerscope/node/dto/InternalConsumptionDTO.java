package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InternalConsumptionDTO extends BaseNodeDTO{
    private Float consumptionSummer;
    private Float consumptionAutumn;
    private Float consumptionWinter;
    private Float consumptionSpring;
}
