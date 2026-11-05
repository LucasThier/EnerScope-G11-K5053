package org.enerscope.economic.service;

import java.math.BigDecimal;
import java.util.*;
import org.enerscope.economic.model.enums.*;
import org.enerscope.economic.model.results.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EconomicIndicatorsCalculatorTest {
    private final EconomicIndicatorsCalculator calculator = new EconomicIndicatorsCalculator();
    private static final BigDecimal WACC = new BigDecimal("0.1");

    static List<PeriodEconomicResult> periods(String... flows) {
        List<PeriodEconomicResult> periods = new ArrayList<>();
        for (int i = 0; i < flows.length; i++)
            periods.add(new PeriodEconomicResult(i, 2030 + i, null, null, null, null, null, null,
                    null, null, null, null, new BigDecimal(flows[i]), BigDecimal.ZERO));
        return periods;
    }

    @Test void completeExampleMatchesIndependentIndicators() {
        var result = new EconomicEngine(new EconomicValidator(), calculator)
                .calculate(EconomicExample.configuration(), EconomicExample.metrics());
        assertEquals(new BigDecimal("169.20"), result.npv());
        var indicators = result.indicators();
        assertEquals(IrrStatus.CALCULATED, indicators.irr().status());
        assertEquals(new BigDecimal("0.1483447840"), indicators.irr().rate());
        assertEquals(new PaybackResult(PaybackStatus.RECOVERED, 4, 2034, false), indicators.simplePayback());
        assertEquals(new PaybackResult(PaybackStatus.RECOVERED, 5, 2035, false), indicators.discountedPayback());
        assertEquals(1, indicators.calculationVersion());
        assertEquals(IndicatorOrigin.STORED, indicators.origin());
    }

    @Test void positiveIrrIsIndependentOfWacc() {
        var flows = periods("-100", "110");
        assertEquals(new BigDecimal("0.1000000000"), calculator.calculate(flows, WACC).irr().rate());
        assertEquals(calculator.calculate(flows, WACC).irr(), calculator.calculate(flows, new BigDecimal("0.9")).irr());
    }

    @Test void negativeIrrIsSupported() {
        assertEquals(new BigDecimal("-0.5000000000"), calculator.calculate(periods("-100", "50"), WACC).irr().rate());
    }

    @Test void zeroIrrIsAValidCalculatedRate() {
        var irr = calculator.calculate(periods("-100", "100"), WACC).irr();
        assertEquals(IrrStatus.CALCULATED, irr.status());
        assertEquals(new BigDecimal("0.0000000000"), irr.rate());
    }

    @Test void emptyIntermediateYearsKeepTheirDiscountExponent() {
        assertEquals(new BigDecimal("0.1000000000"), calculator.calculate(periods("-100", "0", "121"), WACC).irr().rate());
    }

    @Test void flowsWithoutOppositeSignsHaveNoIrr() {
        for (var flows : List.of(periods("0", "0"), periods("100", "20"), periods("-100", "0"), periods("-100", "-20"))) {
            assertEquals(new IrrResult(IrrStatus.NO_SIGN_CHANGE, null), calculator.calculate(flows, WACC).irr());
        }
    }

    @Test void unsupportedSignPatternsDoNotChooseARoot() {
        for (var flows : List.of(periods("-100", "230", "-132"), periods("100", "-110"), periods("0", "-100", "120")))
            assertEquals(new IrrResult(IrrStatus.NON_CONVENTIONAL, null), calculator.calculate(flows, WACC).irr());
    }

    @Test void expansionLimitReturnsNumericalFailureWithoutLosingPayback() {
        var result = calculator.calculate(periods("-1e100", "1"), WACC);
        assertEquals(new IrrResult(IrrStatus.NUMERICAL_FAILURE, null), result.irr());
        assertEquals(PaybackStatus.NOT_RECOVERED_WITHIN_HORIZON, result.simplePayback().status());
    }

    @Test void iterationLimitReturnsNumericalFailure() {
        assertEquals(IrrStatus.NUMERICAL_FAILURE, calculator.calculate(periods("-1", "1e200"), WACC).irr().status());
    }

    @Test void exactRecoveryReturnsFirstAnnualClose() {
        var result = calculator.calculate(periods("-100", "50", "50", "0"), BigDecimal.ZERO);
        assertEquals(new PaybackResult(PaybackStatus.RECOVERED, 2, 2032, false), result.simplePayback());
        assertEquals(result.simplePayback(), result.discountedPayback());
    }

    @Test void unrecoveredInvestmentHasNoInventedDate() {
        var result = calculator.calculate(periods("-100", "20", "20"), WACC);
        assertEquals(new PaybackResult(PaybackStatus.NOT_RECOVERED_WITHIN_HORIZON, null, null, false), result.simplePayback());
        assertEquals(result.simplePayback(), result.discountedPayback());
    }

    @Test void nonnegativeInitialCashMakesPaybackNotApplicable() {
        for (String initial : List.of("0", "100")) {
            var result = calculator.calculate(periods(initial, "-200", "300"), WACC);
            assertEquals(new PaybackResult(PaybackStatus.NOT_APPLICABLE, null, null, false), result.simplePayback());
            assertEquals(result.simplePayback(), result.discountedPayback());
        }
    }

    @Test void laterDeficitDoesNotReplaceFirstRecovery() {
        var result = calculator.calculate(periods("-100", "120", "-200", "500"), WACC);
        assertEquals(new PaybackResult(PaybackStatus.RECOVERED, 1, 2031, true), result.simplePayback());
        assertEquals(result.simplePayback(), result.discountedPayback());
    }

    @Test void zeroWaccMakesBothPaybacksIdentical() {
        var result = calculator.calculate(periods("-1080", "25", "382", "451", "391", "491"), BigDecimal.ZERO);
        assertEquals(result.simplePayback(), result.discountedPayback());
    }

    @Test void displayedRoundingCannotCreateDiscountedRecovery() {
        // 110 / 1.10001 = 99.99909..., displayed as 100.00 but still insufficient.
        var result = calculator.calculate(periods("-100", "110"), new BigDecimal("0.10001"));
        assertEquals(PaybackStatus.RECOVERED, result.simplePayback().status());
        assertEquals(PaybackStatus.NOT_RECOVERED_WITHIN_HORIZON, result.discountedPayback().status());
    }

    @Test void missingOrDiscontinuousPeriodsAreInsufficient() {
        assertEquals(calculator.insufficientData(), calculator.calculate(null, WACC));
        assertEquals(calculator.insufficientData(), calculator.calculate(List.of(), WACC));
        var complete = periods("-100", "0", "120");
        assertEquals(calculator.insufficientData(), calculator.calculate(List.of(complete.getFirst(), complete.getLast()), WACC));
        assertEquals(calculator.insufficientData(), calculator.calculate(Arrays.asList(complete.getFirst(), null), WACC));
        var wrongYear = new PeriodEconomicResult(1, 2032, null,null,null,null,null,null,null,null,null,null,BigDecimal.ONE,null);
        assertEquals(calculator.insufficientData(), calculator.calculate(List.of(complete.getFirst(), wrongYear), WACC));
        var missingCash = new PeriodEconomicResult(0, 2030, null,null,null,null,null,null,null,null,null,null,null,null);
        assertEquals(calculator.insufficientData(), calculator.calculate(List.of(missingCash), WACC));
    }

    @Test void missingWaccOnlyPreventsDiscountedPayback() {
        var result = calculator.calculate(periods("-100", "110"), null);
        assertEquals(IrrStatus.CALCULATED, result.irr().status());
        assertEquals(PaybackStatus.RECOVERED, result.simplePayback().status());
        assertEquals(PaybackStatus.INSUFFICIENT_DATA, result.discountedPayback().status());
    }
}
