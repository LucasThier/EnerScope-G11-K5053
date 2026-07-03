package org.enerscope.money;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable monetary value with a fixed scale of 2 and HALF_UP rounding.
 * Embeddable so it can be reused as a column group on any entity.
 */
@Embeddable
public class MoneyAmount {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal value;

    protected MoneyAmount() {
        this.value = normalize(BigDecimal.ZERO);
    }

    public MoneyAmount(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        this.value = normalize(value);
    }

    public static MoneyAmount of(String value) {
        return new MoneyAmount(new BigDecimal(value));
    }

    public static MoneyAmount of(BigDecimal value) {
        return new MoneyAmount(value);
    }

    public static MoneyAmount of(int value) {
        return new MoneyAmount(BigDecimal.valueOf(value));
    }

    public static MoneyAmount of(long value) {
        return new MoneyAmount(BigDecimal.valueOf(value));
    }

    public static MoneyAmount of(float value) {
        return new MoneyAmount(new BigDecimal(Float.toString(value)));
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public BigDecimal value() {
        return value;
    }

    public MoneyAmount add(MoneyAmount other) {
        return new MoneyAmount(this.value.add(other.value));
    }

    public MoneyAmount subtract(MoneyAmount other) {
        return new MoneyAmount(this.value.subtract(other.value));
    }

    public MoneyAmount multiply(String factor) {
        return multiply(new BigDecimal(factor));
    }

    public MoneyAmount multiply(BigDecimal factor) {
        if (factor == null) {
            throw new IllegalArgumentException("Factor cannot be null");
        }
        return new MoneyAmount(this.value.multiply(factor));
    }

    public MoneyAmount multiply(int factor) {
        return multiply(BigDecimal.valueOf(factor));
    }

    public MoneyAmount divide(String divisor) {
        return divide(new BigDecimal(divisor));
    }

    public MoneyAmount divide(BigDecimal divisor) {
        if (divisor == null) {
            throw new IllegalArgumentException("Divisor cannot be null");
        }
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        BigDecimal result = this.value.divide(divisor, SCALE, ROUNDING);
        return new MoneyAmount(result);
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MoneyAmount that)) {
            return false;
        }
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
