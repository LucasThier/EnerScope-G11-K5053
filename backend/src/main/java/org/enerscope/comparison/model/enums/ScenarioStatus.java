package org.enerscope.comparison.model.enums;

/** Whether a scenario's latest simulation can be compared as it is. */
public enum ScenarioStatus {
    /** The latest simulation was run on the scenario's current configuration and physical model. */
    UP_TO_DATE,
    /** The scenario changed since its latest simulation: it has to be simulated again. */
    OUTDATED,
    /** The scenario has never been simulated. */
    NEVER_SIMULATED,
    /** A simulation of the scenario is queued or running. */
    SIMULATING
}
