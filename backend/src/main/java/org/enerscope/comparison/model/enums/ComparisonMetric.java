package org.enerscope.comparison.model.enums;

import static org.enerscope.comparison.model.enums.MetricCategory.*;
import static org.enerscope.comparison.model.enums.MetricPreference.*;
import static org.enerscope.comparison.model.enums.MetricUnit.*;

/**
 * Indicators the comparison puts side by side. The order of the constants is the
 * order in which they are returned. {@code label} is the Spanish name shown to users.
 */
public enum ComparisonMetric {
    NPV("VAN", FINANCIAL, CURRENCY, HIGHER_IS_BETTER),
    IRR("TIR", FINANCIAL, FRACTION, HIGHER_IS_BETTER),
    ROI("ROI", FINANCIAL, FRACTION, HIGHER_IS_BETTER),
    ROE("ROE", FINANCIAL, FRACTION, HIGHER_IS_BETTER),
    SIMPLE_PAYBACK("Payback simple", FINANCIAL, YEARS, LOWER_IS_BETTER),
    DISCOUNTED_PAYBACK("Payback descontado", FINANCIAL, YEARS, LOWER_IS_BETTER),
    CAPEX("CAPEX", COST, CURRENCY, LOWER_IS_BETTER),
    OPEX("OPEX", COST, CURRENCY, LOWER_IS_BETTER),
    REVENUE("Ingresos", CASH_FLOW, CURRENCY, HIGHER_IS_BETTER),
    TAXES("Impuestos", CASH_FLOW, CURRENCY, NEUTRAL),
    NET_CASH_FLOW("Flujo de caja neto", CASH_FLOW, CURRENCY, HIGHER_IS_BETTER),
    PRODUCED_VOLUME("Volumen producido", PHYSICAL, SIMULATOR_UNIT, HIGHER_IS_BETTER),
    EXPORTED_VOLUME("Volumen exportado", PHYSICAL, SIMULATOR_UNIT, HIGHER_IS_BETTER),
    LOSSES("Pérdidas", PHYSICAL, SIMULATOR_UNIT, LOWER_IS_BETTER),
    OPERATING_HOURS("Horas de operación", PHYSICAL, HOURS, NEUTRAL),
    EVENTS("Eventos de mantenimiento/falla", PHYSICAL, COUNT, LOWER_IS_BETTER);

    private final String label;
    private final MetricCategory category;
    private final MetricUnit unit;
    private final MetricPreference preference;

    ComparisonMetric(String label, MetricCategory category, MetricUnit unit, MetricPreference preference) {
        this.label = label;
        this.category = category;
        this.unit = unit;
        this.preference = preference;
    }

    public String label() { return label; }
    public MetricCategory category() { return category; }
    public MetricUnit unit() { return unit; }
    public MetricPreference preference() { return preference; }
}
