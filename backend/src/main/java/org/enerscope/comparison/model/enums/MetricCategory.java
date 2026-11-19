package org.enerscope.comparison.model.enums;

/** Group a compared indicator belongs to, so clients can lay the comparison out by section. */
public enum MetricCategory {
    /** Profitability and recovery: NPV, IRR, ROI, ROE and paybacks. */
    FINANCIAL,
    /** Investment and operating costs: CAPEX and OPEX. */
    COST,
    /** Undiscounted cash totals over the horizon: revenue, taxes and net cash flow. */
    CASH_FLOW,
    /** Simulated physical flows, in raw simulator units. */
    PHYSICAL
}
