package org.enerscope.node.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

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
    // Pinned JSON names: the all-caps getters (getDTMTime/getDTMCost) otherwise
    // mangle to "dtmtime"/"dtmcost", which is easy to get wrong from clients.
    @JsonProperty("dtmTime")
    private int DTMTime;
    @JsonProperty("dtmCost")
    private Float DTMCost;
    private Float surface;
}