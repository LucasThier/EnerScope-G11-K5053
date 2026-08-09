package org.enerscope.node.dto;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.money.MoneyAmount;

/**
 * Data Transfer Object for Well node type.
 * Contains all fields needed to create a Well.
 */
@Getter
@Setter
public class WellDTO extends BaseNodeDTO {

    // Well-specific fields
    private Float maxCollectionCapacity;
    private Float declineCurve;
    private Float gasRichness;
    private int DTMTime;
    private MoneyAmount DTMCost;
    private Float surface;
}