package org.enerscope.economic.model.results;

import org.enerscope.economic.model.enums.IndicatorOrigin;

public record EconomicIndicators(IrrResult irr,
                                 PaybackResult simplePayback,
                                 PaybackResult discountedPayback,
                                 int calculationVersion,
                                 IndicatorOrigin origin) {

    public EconomicIndicators withOrigin(IndicatorOrigin value) {
        return new EconomicIndicators(irr, simplePayback, discountedPayback, calculationVersion, value);
    }

}
