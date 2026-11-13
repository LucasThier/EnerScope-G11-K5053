package org.enerscope.comparison.model.enums;

/** Kinds of relevant difference the comparison reports. */
public enum FindingType {
    SIGNIFICANT_IMPROVEMENT,
    SIGNIFICANT_DETERIORATION,
    SIGNIFICANT_CHANGE,
    /** NPV is positive on one side and not on the other. */
    NPV_SIGN_CHANGE,
    /** An indicator can be calculated on one side but not on the other (e.g. payback never recovered). */
    AVAILABILITY_CHANGE,
    NODE_ADDED,
    NODE_REMOVED,
    NODE_FLOW_CHANGE
}
