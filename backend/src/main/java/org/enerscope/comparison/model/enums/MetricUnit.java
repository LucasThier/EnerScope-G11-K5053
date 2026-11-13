package org.enerscope.comparison.model.enums;

/** How a compared value must be read. Rates are fractions (0.15 means 15%), never percentages. */
public enum MetricUnit {
    CURRENCY,
    FRACTION,
    YEARS,
    SIMULATOR_UNIT,
    HOURS,
    COUNT
}
