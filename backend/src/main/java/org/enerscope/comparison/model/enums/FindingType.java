package org.enerscope.comparison.model.enums;

/** Kinds of relevant difference among the compared scenarios. */
public enum FindingType {
    /** Some scenarios create value (positive NPV) and others lose it. */
    NPV_SIGN_SPLIT,
    /** An indicator exists for some scenarios and not for others for a real reason (e.g. payback never recovered). */
    AVAILABILITY_SPLIT,
    /** The gap between the best and the worst scenario is relevant. */
    LARGE_GAP,
    /** A physical node exists in some scenarios only. */
    NODE_PARTIAL,
    /** A node's output differs relevantly across scenarios. */
    NODE_FLOW_GAP
}
