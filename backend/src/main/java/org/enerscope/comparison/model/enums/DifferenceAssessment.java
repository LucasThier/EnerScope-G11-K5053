package org.enerscope.comparison.model.enums;

/** How a scenario's value reads against the baseline's. */
public enum DifferenceAssessment {
    /** The cell belongs to the baseline itself. */
    BASELINE,
    EQUAL,
    BETTER,
    WORSE,
    /** Different, for an indicator with no favorable direction. */
    CHANGED,
    /** One side is unavailable, or the values are in different currencies. */
    NOT_COMPARABLE
}
