package org.enerscope.comparison.model.enums;

/** Where a scenario's value stands among all the compared scenarios for one indicator. No scenario is a reference. */
public enum Standing {
    /** The most favorable value (several scenarios can share it). */
    BEST,
    /** Neither the best nor the worst. */
    MIDDLE,
    /** The least favorable value. */
    WORST,
    /** Every scenario has the same value. */
    TIED,
    /** Not ranked: the indicator has no favorable direction (taxes, hours) or the value is unavailable. */
    UNRANKED,
    /** Amounts in different currencies cannot be ranked against each other. */
    NOT_COMPARABLE
}
