package org.enerscope.economic.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import org.enerscope.economic.model.enums.*;
import org.enerscope.economic.model.results.*;
import org.springframework.stereotype.Service;

/** Annual indicators computed only from the consolidated, after-tax cash flows. */
@Service
public class EconomicIndicatorsCalculator {
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal WIDTH_TOLERANCE = new BigDecimal("1e-24");
    private static final BigDecimal RESIDUAL_TOLERANCE = new BigDecimal("1e-20");
    private static final int MAX_EXPANSIONS = 256;
    private static final int MAX_ITERATIONS = 512;

    public EconomicIndicators calculate(List<PeriodEconomicResult> periods, BigDecimal wacc) {
        if (!complete(periods)) return insufficientData();
        IrrResult irr;
        try { irr = irr(periods); }
        catch (ArithmeticException failure) { irr = new IrrResult(IrrStatus.NUMERICAL_FAILURE, null); }
        PaybackResult simple = payback(periods, null);
        PaybackResult discounted;
        if (wacc == null || wacc.signum() < 0) {
            discounted = unavailable(PaybackStatus.INSUFFICIENT_DATA);
        } else {
            try { discounted = payback(periods, wacc); }
            catch (ArithmeticException failure) { discounted = unavailable(PaybackStatus.NUMERICAL_FAILURE); }
        }
        return new EconomicIndicators(irr, simple, discounted, 1, IndicatorOrigin.STORED);
    }

    public EconomicIndicators insufficientData() {
        return new EconomicIndicators(new IrrResult(IrrStatus.INSUFFICIENT_DATA, null),
                unavailable(PaybackStatus.INSUFFICIENT_DATA), unavailable(PaybackStatus.INSUFFICIENT_DATA),
                1, IndicatorOrigin.STORED);
    }

    private boolean complete(List<PeriodEconomicResult> periods) {
        if (periods == null || periods.isEmpty() || periods.getFirst() == null) return false;
        int startYear = periods.getFirst().year();
        for (int i = 0; i < periods.size(); i++) {
            var p = periods.get(i);
            if (p == null || p.cashFlow() == null || p.period() != i
                    || (long) p.year() != (long) startYear + i) return false;
        }
        return true;
    }

    private IrrResult irr(List<PeriodEconomicResult> periods) {
        boolean positive = false, negative = false;
        for (var p : periods) {
            positive |= p.cashFlow().signum() > 0;
            negative |= p.cashFlow().signum() < 0;
        }
        if (!positive || !negative) return new IrrResult(IrrStatus.NO_SIGN_CHANGE, null);
        if (periods.getFirst().cashFlow().signum() >= 0
                || periods.stream().skip(1).anyMatch(p -> p.cashFlow().signum() < 0))
            return new IrrResult(IrrStatus.NON_CONVENTIONAL, null);

        BigDecimal low = ZERO, high = ONE;
        BigDecimal highValue = polynomial(periods, high);
        for (int i = 0; highValue.signum() < 0 && i < MAX_EXPANSIONS; i++) {
            high = high.multiply(TWO, MC);
            highValue = polynomial(periods, high);
        }
        if (highValue.signum() < 0) return new IrrResult(IrrStatus.NUMERICAL_FAILURE, null);
        BigDecimal scale = periods.getFirst().cashFlow().abs();
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            BigDecimal x = low.add(high, MC).divide(TWO, MC);
            BigDecimal value = polynomial(periods, x);
            // An exact root makes a zero-width bracket and meets both criteria.
            if (value.signum() == 0) low = high = x;
            else if (value.signum() < 0) low = x;
            else high = x;
            BigDecimal relativeWidth = high.subtract(low, MC).divide(x, MC);
            BigDecimal relativeResidual = value.abs().divide(scale, MC);
            if (relativeWidth.compareTo(WIDTH_TOLERANCE) < 0
                    && relativeResidual.compareTo(RESIDUAL_TOLERANCE) < 0) {
                BigDecimal rate = ONE.divide(x, MC).subtract(ONE, MC).setScale(10, RoundingMode.HALF_UP);
                return new IrrResult(IrrStatus.CALCULATED, rate);
            }
        }
        return new IrrResult(IrrStatus.NUMERICAL_FAILURE, null);
    }

    private BigDecimal polynomial(List<PeriodEconomicResult> periods, BigDecimal x) {
        BigDecimal value = ZERO;
        for (int i = periods.size() - 1; i >= 0; i--)
            value = value.multiply(x, MC).add(periods.get(i).cashFlow(), MC);
        return value;
    }

    private PaybackResult payback(List<PeriodEconomicResult> periods, BigDecimal wacc) {
        if (periods.getFirst().cashFlow().signum() >= 0) return unavailable(PaybackStatus.NOT_APPLICABLE);
        BigDecimal cumulative = ZERO;
        Integer recoveredPeriod = null, recoveredYear = null;
        boolean negativeAgain = false;
        for (var p : periods) {
            BigDecimal amount = p.cashFlow();
            if (wacc != null) amount = amount.divide(ONE.add(wacc).pow(p.period(), MC), MC);
            cumulative = cumulative.add(amount);
            if (recoveredPeriod == null && cumulative.signum() >= 0) {
                recoveredPeriod = p.period(); recoveredYear = p.year();
            } else if (recoveredPeriod != null && cumulative.signum() < 0) negativeAgain = true;
        }
        return recoveredPeriod == null ? unavailable(PaybackStatus.NOT_RECOVERED_WITHIN_HORIZON)
                : new PaybackResult(PaybackStatus.RECOVERED, recoveredPeriod, recoveredYear, negativeAgain);
    }

    private PaybackResult unavailable(PaybackStatus status) {
        return new PaybackResult(status, null, null, false);
    }
}
