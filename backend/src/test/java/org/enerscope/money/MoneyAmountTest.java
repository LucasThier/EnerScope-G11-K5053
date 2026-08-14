package org.enerscope.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyAmountTest {

    @Test
    void normalizesToTwoDecimalsWithHalfUpRounding() {
        assertEquals(new BigDecimal("2.35"), MoneyAmount.of("2.345").value());
        assertEquals(new BigDecimal("2.34"), MoneyAmount.of("2.344").value());
    }

    @Test
    void addsAndSubtracts() {
        MoneyAmount a = MoneyAmount.of("10.00");
        MoneyAmount b = MoneyAmount.of("2.50");
        assertEquals(MoneyAmount.of("12.50"), a.add(b));
        assertEquals(MoneyAmount.of("7.50"), a.subtract(b));
    }

    @Test
    void multipliesAndDivides() {
        assertEquals(MoneyAmount.of("30.00"), MoneyAmount.of("10.00").multiply(3));
        assertEquals(MoneyAmount.of("5.00"), MoneyAmount.of("10.00").divide("2"));
    }

    @Test
    void rejectsNullValue() {
        assertThrows(IllegalArgumentException.class, () -> new MoneyAmount(null));
    }

    @Test
    void rejectsDivisionByZero() {
        assertThrows(ArithmeticException.class, () -> MoneyAmount.of("10.00").divide("0"));
    }

    @Test
    void equalityIsValueBased() {
        assertEquals(MoneyAmount.of("1.00"), MoneyAmount.of(1));
        assertEquals(MoneyAmount.of("1.00").hashCode(), MoneyAmount.of(1).hashCode());
    }

    @Test
    void addsAll() {
        List<MoneyAmount> moneyAmounts = new ArrayList<>();
        moneyAmounts.add(MoneyAmount.of(10));
        moneyAmounts.add(MoneyAmount.of("5.5"));
        MoneyAmount amount = MoneyAmount.of(1);
        assertEquals(MoneyAmount.of("16.5"), amount.addAll(moneyAmounts));
    }

}
